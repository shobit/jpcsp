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
package jpcsp.HLE.VFS.local;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.Map;

import jpcsp.HLE.TPointer;
import jpcsp.HLE.VFS.AbstractVirtualFile;
import jpcsp.HLE.VFS.IVirtualFile;
import jpcsp.HLE.modules.IoFileMgrForUser;
import jpcsp.HLE.modules.IoFileMgrForUser.IoOperation;
import jpcsp.HLE.modules.IoFileMgrForUser.IoOperationTiming;
import jpcsp.filesystems.SeekableRandomFile;
import jpcsp.util.Utilities;

public class LocalVirtualFile extends AbstractVirtualFile {
	protected SeekableRandomFile file;
	protected boolean truncateAtNextWrite;

	public LocalVirtualFile(SeekableRandomFile file) {
		super(file);
		this.file = file;
	}

	@Override
	public int ioWrite(TPointer inputPointer, int inputLength) {
		try {
			Utilities.write(file, inputPointer.getAddress(), inputLength);
		} catch (IOException e) {
			log.error("ioWrite", e);
			return IO_ERROR;
		}

		return inputLength;
	}

	@Override
	public int ioWrite(byte[] inputBuffer, int inputOffset, int inputLength) {
		try {
			if (isTruncateAtNextWrite()) {
            	// The file was open with PSP_O_TRUNC: truncate the file at the first write
				long position = getPosition();
				if (position < file.length()) {
					file.setLength(getPosition());
				}
				setTruncateAtNextWrite(false);
			}

			file.write(inputBuffer, inputOffset, inputLength);
		} catch (IOException e) {
			log.error("ioWrite", e);
			return IO_ERROR;
		}

		return inputLength;
	}

	@Override
	public int ioIoctl(int command, TPointer inputPointer, int inputLength, TPointer outputPointer, int outputLength) {
		int result;
		switch (command) {
			case 0x00005001:
	        	if (inputLength != 0 || outputLength != 0) {
	        		result = IO_ERROR;
	        	} else {
	        		result = 0;
	        	}
				break;
            // Check if LoadExec is allowed on the file
            case 0x00208013:
            	if (log.isDebugEnabled()) {
            		log.debug(String.format("Checking if LoadExec is allowed on '%s'", this));
            	}
            	// Result == 0: LoadExec allowed
            	// Result != 0: LoadExec prohibited
            	result = 0;
            	break;
            // Check if LoadModule is allowed on the file
            case 0x00208003:
            	if (log.isDebugEnabled()) {
            		log.debug(String.format("Checking if LoadModule is allowed on '%s'", this));
            	}
            	// Result == 0: LoadModule allowed
            	// Result != 0: LoadModule prohibited
            	result = 0;
            	break;
            // Check if PRX type is allowed on the file
            case 0x00208081:
            case 0x00208082:
            	if (log.isDebugEnabled()) {
            		log.debug(String.format("Checking if PRX type is allowed on '%s'", this));
            	}
            	// Result == 0: PRX type allowed
            	// Result != 0: PRX type prohibited
            	result = 0;
            	break;
			default:
				result = super.ioIoctl(command, inputPointer, inputLength, outputPointer, outputLength);
				break;
		}

		return result;
	}

	public boolean isTruncateAtNextWrite() {
		return truncateAtNextWrite;
	}

	public void setTruncateAtNextWrite(boolean truncateAtNextWrite) {
		this.truncateAtNextWrite = truncateAtNextWrite;
	}

	@Override
	public IVirtualFile duplicate() {
		try {
			return new LocalVirtualFile(new SeekableRandomFile(file.getFileName(), file.getMode()));
		} catch (FileNotFoundException e) {
		}

		return super.duplicate();
	}

	@Override
	public Map<IoOperation, IoOperationTiming> getTimings() {
		return IoFileMgrForUser.noDelayTimings;
	}

	@Override
	public String toString() {
		return String.format("LocalVirtualFile %s", file);
	}
}
