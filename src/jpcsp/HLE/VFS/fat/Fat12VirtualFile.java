/*
This file is part of jpcsp.

Jpcsp is free software: you can redistribute it and/or modify
it under the terms of the GNU General Public License as published by
the Free Software Foundation, either version 3 of the License, or
(at your option) any later version.

Jpcsp is distributed in the hope that it will be useful,
but WITHOUT ANY WARRANTY; without even the implied warranty of
MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
GNU General Public License for more details.

You should have received a copy of the GNU General Public License
along with Jpcsp.  If not, see <http://www.gnu.org/licenses/>.
 */
package jpcsp.HLE.VFS.fat;

import static jpcsp.HLE.VFS.fat.FatBuilder.numberOfFats;
import static jpcsp.HLE.VFS.fat.FatBuilder.reservedSectors;
import static jpcsp.HLE.VFS.fat.FatUtils.readSectorInt8;
import static jpcsp.HLE.VFS.fat.FatUtils.storeSectorInt16;
import static jpcsp.HLE.VFS.fat.FatUtils.storeSectorInt32;
import static jpcsp.HLE.VFS.fat.FatUtils.storeSectorInt8;
import static jpcsp.HLE.VFS.fat.FatUtils.storeSectorString;
import static jpcsp.util.Utilities.alignUp;

import jpcsp.HLE.VFS.IVirtualFileSystem;

public class Fat12VirtualFile extends FatVirtualFile {
	public Fat12VirtualFile(String deviceName, IVirtualFileSystem vfs, int totalSectors) {
		super(deviceName, vfs, totalSectors);
		// FAT12 has no FS Info sector
		setFsInfoSectorNumber(-1);

		// The FAT is directly after the boot sector, no reserved sectors present
//		setFatSectorNumber(FatBuilder.bootSectorNumber + 1);
	}

	@Override
	protected int getClusterMask() {
		return 0x00000FFF;
	}

	@Override
	protected int getFatEOC() {
		return 0xFF8; // Last cluster in file (EOC)
	}

	@Override
	protected String getOEMName() {
		return "6600-FAT";
	}

	@Override
	protected int getSectorsPerCluster() {
		return 32;
	}

	@Override
	protected int getFatSectors(int totalSectors, int sectorsPerCluster) {
		return 0x20;
    }

	@Override
	protected void readBIOSParameterBlock() {
    	// Bytes per sector
    	storeSectorInt16(currentSector, 11, sectorSize);

    	// Sectors per cluster
    	storeSectorInt8(currentSector, 13, getSectorsPerCluster());

    	// Reserved sectors
    	storeSectorInt16(currentSector, 14, reservedSectors);

    	// Number of File Allocation Tables (FATs)
    	storeSectorInt8(currentSector, 16, numberOfFats);

    	// Max entries in root dir
    	storeSectorInt16(currentSector, 17, 0x200);

    	// Total sectors
    	storeSectorInt16(currentSector, 19, totalSectors);

    	// Media type
    	storeSectorInt8(currentSector, 21, 0xF8); // Fixed disk

    	// Count of sectors used by the FAT table
    	final int fatSectors = getFatSectors(totalSectors, getSectorsPerCluster());
    	storeSectorInt16(currentSector, 22, fatSectors);

    	// Sectors per track (default)
    	storeSectorInt16(currentSector, 24, 0x20);

    	// Number of heads (default)
    	storeSectorInt16(currentSector, 26, 0x40);

    	// Count of hidden sectors
    	storeSectorInt32(currentSector, 28, 0);

    	// Total sectors
    	storeSectorInt32(currentSector, 32, 4);

    	// Physical driver number (0x80 for first fixed disk)
    	storeSectorInt8(currentSector, 36, 0x80);

    	// Reserved
    	storeSectorInt8(currentSector, 37, 0);

    	// Extended boot signature
    	storeSectorInt8(currentSector, 38, 0x29);

    	// Volume ID
    	storeSectorInt32(currentSector, 39, 0x06060002);

    	// Partition Volume Label
    	storeSectorString(currentSector, 43, "NO NAME", 11);

    	// File system type
    	storeSectorString(currentSector, 54, "FAT12", 8);
	}

	private void storeFatByte(int offset, int value) {
		if (offset >= 0 && offset < sectorSize) {
			storeSectorInt8(currentSector, offset, value & 0xFF);
		}
	}

	@Override
	protected void readFatSector(int fatIndex) {
		readEmptySector();

		int offset = (fatIndex * sectorSize) / 3 * 2;
		int startIndex = (offset / 2 * 3) - (fatIndex * sectorSize);
		for (int i = startIndex, j = 0; i < sectorSize; j += 2) {
			int value = 0;
			if (offset + j < fatClusterMap.length) {
				value = fatClusterMap[offset + j];
				if (offset + j + 1 < fatClusterMap.length) {
					value |= fatClusterMap[offset + j + 1] << 12;
				}
			}

			// Store 3 bytes representing two FAT12 entries
			storeFatByte(i, value);
			i++;
			storeFatByte(i, value >> 8);
			i++;
			storeFatByte(i, value >> 16);
			i++;
		}
	}

	@Override
	protected void writeFatSector(int fatIndex) {
		// TODO Implement the change of the FAT cluster number overlapping 2 sectors
		int offset = alignUp((fatIndex * sectorSize * 2) / 3, 1);
		int startIndex = (offset / 2 * 3) - (fatIndex * sectorSize);
		for (int i = startIndex, j = 0; i < sectorSize - 2; j += 2, i += 3) {
			int value = readSectorInt8(currentSector, i);
			value |= readSectorInt8(currentSector, i + 1) << 8;
			value |= readSectorInt8(currentSector, i + 2) << 16;

			int fatEntry1 = value & 0xFFF;
			if (fatEntry1 != fatClusterMap[offset + j]) {
				writeFatSectorEntry(offset + j, fatEntry1);
			}

			int fatEntry2 = value >> 12;
			if (fatEntry2 != fatClusterMap[offset + j + 1]) {
				writeFatSectorEntry(offset + j + 1, fatEntry2);
			}
		}
	}
}
