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
package jpcsp.HLE.modules;

import java.nio.ByteBuffer;
import java.util.HashSet;
import java.util.Set;

import org.apache.log4j.Logger;

import jpcsp.HLE.BufferInfo;
import jpcsp.HLE.CanBeNull;
import jpcsp.HLE.HLEFunction;
import jpcsp.HLE.HLELogging;
import jpcsp.HLE.HLEModule;
import jpcsp.HLE.Modules;
import jpcsp.HLE.PspString;
import jpcsp.HLE.TPointer;
import jpcsp.HLE.TPointer32;
import jpcsp.HLE.BufferInfo.Usage;
import jpcsp.HLE.VFS.IVirtualFile;
import jpcsp.HLE.VFS.IVirtualFileSystem;
import jpcsp.HLE.kernel.Managers;
import jpcsp.HLE.kernel.types.SceKernelErrors;
import jpcsp.HLE.kernel.types.SceKernelLMOption;
import jpcsp.HLE.kernel.types.SceModule;
import jpcsp.HLE.modules.ModuleMgrForUser.LoadModuleContext;
import jpcsp.HLE.modules.SysMemUserForUser.SysMemInfo;
import jpcsp.util.Utilities;

public class ModuleMgrForKernel extends HLEModule {
	public static Logger log = Modules.getLogger("ModuleMgrForKernel");
	private Set<String> modulesWithMemoryAllocated;

	@Override
	public void start() {
		modulesWithMemoryAllocated = new HashSet<>();

		super.start();
	}

	public boolean isMemoryAllocatedForModule(String moduleName) {
		if (modulesWithMemoryAllocated == null) {
			return false;
		}
		return modulesWithMemoryAllocated.contains(moduleName);
	}

	@HLEFunction(nid = 0xBA889C07, version = 150)
    public int sceKernelLoadModuleBuffer(TPointer buffer, int bufSize, int flags, @CanBeNull TPointer optionAddr) {
        SceKernelLMOption lmOption = null;
        if (optionAddr.isNotNull()) {
            lmOption = new SceKernelLMOption();
            lmOption.read(optionAddr);
            if (log.isInfoEnabled()) {
                log.info(String.format("sceKernelLoadModuleBuffer options: %s", lmOption));
            }
        }

        LoadModuleContext loadModuleContext = new LoadModuleContext();
        loadModuleContext.fileName = buffer.toString();
        loadModuleContext.flags = flags;
        loadModuleContext.buffer = buffer.getAddress();
        loadModuleContext.bufferSize = bufSize;
        loadModuleContext.lmOption = lmOption;
        loadModuleContext.needModuleInfo = true;
        loadModuleContext.allocMem = true;

        return Modules.ModuleMgrForUserModule.hleKernelLoadModule(loadModuleContext);
    }

	/**
	 * Load a module with the VSH apitype.
	 *
	 * @param path        The path to the module to load.
	 * @param flags       Unused, always 0 . 
	 * @param optionAddr  Pointer to a mod_param_t structure. Can be NULL.
	 * @return
	 */
	@HLELogging(level = "info")
	@HLEFunction(nid = 0xD5DDAB1F, version = 150)
	public int sceKernelLoadModuleVSH(PspString path, int flags, @CanBeNull TPointer optionAddr) {
        SceKernelLMOption lmOption = null;
        if (optionAddr.isNotNull()) {
            lmOption = new SceKernelLMOption();
            lmOption.read(optionAddr);
            if (log.isInfoEnabled()) {
                log.info(String.format("sceKernelLoadModuleVSH options: %s", lmOption));
            }
        }

        LoadModuleContext loadModuleContext = new LoadModuleContext();
        loadModuleContext.fileName = path.getString();
        loadModuleContext.flags = flags;
        loadModuleContext.lmOption = lmOption;
        loadModuleContext.needModuleInfo = true;
        loadModuleContext.allocMem = true;

        return Modules.ModuleMgrForUserModule.hleKernelLoadModule(loadModuleContext);
	}

	@HLEFunction(nid = 0xD86DD11B, version = 150)
	public int sceKernelSearchModuleByName(PspString name) {
		SceModule module = Managers.modules.getModuleByName(name.getString());
		if (module == null) {
			return SceKernelErrors.ERROR_KERNEL_UNKNOWN_MODULE;
		}

		return module.modid;
	}

    @HLEFunction(nid = 0x939E4270, version = 150, checkInsideInterrupt = true)
    public int sceKernelLoadModule_660(PspString path, int flags, @CanBeNull TPointer optionAddr) {
    	return Modules.ModuleMgrForUserModule.sceKernelLoadModule(path, flags, optionAddr);
    }

    @HLEFunction(nid = 0x387E3CA9, version = 150, checkInsideInterrupt = true)
    public int sceKernelUnloadModule_660(int uid) {
    	return Modules.ModuleMgrForUserModule.sceKernelUnloadModule(uid);
    }

    @HLEFunction(nid = 0x3FF74DF1, version = 150, checkInsideInterrupt = true)
    public int sceKernelStartModule_660(int uid, int argSize, @CanBeNull TPointer argp, @CanBeNull TPointer32 statusAddr, @CanBeNull TPointer optionAddr) {
    	return Modules.ModuleMgrForUserModule.sceKernelStartModule(uid, argSize, argp, statusAddr, optionAddr);
    }

    @HLEFunction(nid = 0xE5D6087B, version = 150, checkInsideInterrupt = true)
    public int sceKernelStopModule_660(int uid, int argSize, @CanBeNull TPointer argp, @CanBeNull TPointer32 statusAddr, @CanBeNull TPointer optionAddr) {
    	return Modules.ModuleMgrForUserModule.sceKernelStopModule(uid, argSize, argp, statusAddr, optionAddr);
    }

    @HLEFunction(nid = 0xD4EE2D26, version = 150, checkInsideInterrupt = true)
    public int sceKernelLoadModuleToBlock(PspString path, int blockId, @BufferInfo(usage=Usage.out) TPointer32 separatedBlockId, int unknown2, @CanBeNull TPointer optionAddr) {
        SceKernelLMOption lmOption = null;
        if (optionAddr.isNotNull()) {
            lmOption = new SceKernelLMOption();
            lmOption.read(optionAddr);
            if (log.isInfoEnabled()) {
            	log.info(String.format("sceKernelLoadModuleToBlock options: %s", lmOption));
            }
        }

        SysMemInfo sysMemInfo = Modules.SysMemUserForUserModule.getSysMemInfo(blockId);
        if (sysMemInfo == null) {
        	return -1;
        }

        if (log.isDebugEnabled()) {
        	log.debug(String.format("sceKernelLoadModuleToBlock sysMemInfo=%s", sysMemInfo));
        }

        modulesWithMemoryAllocated.add(path.getString());

    	// If we cannot load the module file, return the same blockId
    	separatedBlockId.setValue(blockId);

        StringBuilder localFileName = new StringBuilder();
        IVirtualFileSystem vfs = Modules.IoFileMgrForUserModule.getVirtualFileSystem(path.getString(), localFileName);
        if (vfs != null) {
        	IVirtualFile vFile = vfs.ioOpen(localFileName.toString(), IoFileMgrForUser.PSP_O_RDONLY, 0);
        	if (vFile != null) {
	        	byte[] bytes = new byte[(int) vFile.length()];
	        	int length = vFile.ioRead(bytes, 0, bytes.length);
	        	ByteBuffer moduleBuffer = ByteBuffer.wrap(bytes, 0, length);

	        	SceModule module = Modules.ModuleMgrForUserModule.getModuleInfo(path.getString(), moduleBuffer, sysMemInfo.partitionid, sysMemInfo.partitionid);
	        	if (module != null) {
	        		int size = Modules.ModuleMgrForUserModule.getModuleRequiredMemorySize(module);

	        		if (log.isDebugEnabled()) {
	        			log.debug(String.format("sceKernelLoadModuleToBlock module requiring 0x%X bytes", size));
	        		}

	        		// Aligned on 256 bytes boundary
	    	        size = Utilities.alignUp(size, 0xFF);
	    	        SysMemInfo separatedSysMemInfo = Modules.SysMemUserForUserModule.separateMemoryBlock(sysMemInfo, size);
	    	        // This is the new blockId after calling sceKernelSeparateMemoryBlock
	    	        separatedBlockId.setValue(separatedSysMemInfo.uid);

	    	        if (log.isDebugEnabled()) {
	    	        	log.debug(String.format("sceKernelLoadModuleToBlock separatedSysMemInfo=%s", separatedSysMemInfo));
	    	        }
	        	}
        	}
        }

        LoadModuleContext loadModuleContext = new LoadModuleContext();
        loadModuleContext.fileName = path.getString();
        loadModuleContext.lmOption = lmOption;
        loadModuleContext.needModuleInfo = true;
        loadModuleContext.allocMem = false;
        loadModuleContext.baseAddr = sysMemInfo.addr;
        loadModuleContext.basePartition = sysMemInfo.partitionid;

        return Modules.ModuleMgrForUserModule.hleKernelLoadModule(loadModuleContext);
    }
}
