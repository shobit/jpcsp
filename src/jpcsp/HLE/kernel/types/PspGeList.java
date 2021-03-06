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
package jpcsp.HLE.kernel.types;

import static jpcsp.HLE.modules.sceGe_user.PSP_GE_LIST_CANCEL_DONE;
import static jpcsp.HLE.modules.sceGe_user.PSP_GE_LIST_DONE;
import static jpcsp.HLE.modules.sceGe_user.PSP_GE_LIST_DRAWING;
import static jpcsp.HLE.modules.sceGe_user.PSP_GE_LIST_QUEUED;
import static jpcsp.HLE.modules.sceGe_user.PSP_GE_LIST_STALL_REACHED;
import static jpcsp.HLE.modules.sceGe_user.PSP_GE_LIST_STRINGS;

import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

import jpcsp.HLE.Modules;
import jpcsp.HLE.modules.sceGe_user;
import jpcsp.graphics.VideoEngine;
import jpcsp.graphics.RE.externalge.ExternalGE;
import jpcsp.memory.IMemoryReader;
import jpcsp.memory.MemoryReader;
import jpcsp.Memory;
import jpcsp.MemoryMap;

public class PspGeList {
	private VideoEngine videoEngine;
	private static final int pcAddressMask = 0xFFFFFFFC & Memory.addressMask;
    public int list_addr;
    private int stall_addr;
    public int cbid;
    public pspGeListOptParam optParams;
    private int stackAddr;

    private int pc;

    // a stack entry contains the PC and the baseOffset
    private int[] stack = new int[32*2];
    private int stackIndex;

    public int status;
    public int id;

    public List<Integer> blockedThreadIds; // the threads we are blocking
    private boolean finished;
    private boolean paused;
    private boolean ended;
    private boolean reset;
    private boolean restarted;
    private Semaphore sync; // Used for async display
    private IMemoryReader memoryReader;
    private int saveContextAddr;
    private IMemoryReader baseMemoryReader;
    private int baseMemoryReaderStartAddress;
    private int baseMemoryReaderEndAddress;

    public PspGeList(int id) {
    	videoEngine = VideoEngine.getInstance();
    	this.id = id;
    	blockedThreadIds = new LinkedList<Integer>();
    	reset();
    }

    private void init() {
    	stackIndex = 0;
    	blockedThreadIds.clear();
    	finished = true;
    	paused = false;
    	reset = true;
        ended = true;
        restarted = false;
        memoryReader = null;
        baseMemoryReader = null;
        baseMemoryReaderStartAddress = 0;
        baseMemoryReaderEndAddress = 0;
        pc = 0;
        saveContextAddr = 0;
    }

    public void init(int list_addr, int stall_addr, int cbid, pspGeListOptParam optParams) {
        init();

        list_addr &= pcAddressMask;
        stall_addr &= pcAddressMask;

        this.list_addr = list_addr;
        this.stall_addr = stall_addr;
        this.cbid = cbid;
        this.optParams = optParams;

        if (optParams != null) {
        	stackAddr = optParams.stackAddr;
        } else {
        	stackAddr = 0;
        }
        setPc(list_addr);
        status = (pc == stall_addr) ? PSP_GE_LIST_STALL_REACHED : PSP_GE_LIST_QUEUED;
    	finished = false;
    	reset = false;
        ended = false;

    	sync = new Semaphore(0);
    }

    public void reset() {
    	status = PSP_GE_LIST_DONE;
    	init();
    }

    public void pushSignalCallback(int listId, int behavior, int signal) {
    	int listPc = getPc();
    	if (!ExternalGE.isActive()) {
    		// PC address after the END command
    		listPc += 4;
    	}
        Modules.sceGe_userModule.triggerSignalCallback(cbid, listId, listPc, behavior, signal);
    }

    public void pushFinishCallback(int listId, int arg) {
    	int listPc = getPc();
    	if (!ExternalGE.isActive()) {
    		// PC address after the END command
    		listPc += 4;
    	}
    	Modules.sceGe_userModule.triggerFinishCallback(cbid, listId, listPc, arg);
    }

    private void pushStack(int value) {
    	stack[stackIndex++] = value;
    }

    private int popStack() {
    	return stack[--stackIndex];
    }

    public int getAddressRel(int argument) {
    	return Memory.normalizeAddress((videoEngine.getBase() | argument));
    }

    public int getAddressRelOffset(int argument) {
    	return Memory.normalizeAddress((videoEngine.getBase() | argument) + videoEngine.getBaseOffset());
    }

    public boolean isStackEmpty() {
    	return stackIndex <= 0;
    }

    public void setPc(int pc) {
    	pc &= pcAddressMask;
    	if (this.pc != pc) {
    		int oldPc = this.pc;
    		this.pc = pc;
    		resetMemoryReader(oldPc);
    	}
    }

	public int getPc() {
		return pc;
	}

    public void jumpAbsolute(int argument) {
    	setPc(Memory.normalizeAddress(argument));
    }

    public void jumpRelative(int argument) {
    	setPc(getAddressRel(argument));
    }

    public void jumpRelativeOffset(int argument) {
    	setPc(getAddressRelOffset(argument));
    }

    public void callAbsolute(int argument) {
    	pushStack(pc);
    	pushStack(videoEngine.getBaseOffset());
    	jumpAbsolute(argument);
    }

    public void callRelative(int argument) {
    	pushStack(pc);
    	pushStack(videoEngine.getBaseOffset());
    	jumpRelative(argument);
    }

    public void callRelativeOffset(int argument) {
    	pushStack(pc);
    	pushStack(videoEngine.getBaseOffset());
    	jumpRelativeOffset(argument);
    }

    public void ret() {
    	if (!isStackEmpty()) {
    		videoEngine.setBaseOffset(popStack());
    		setPc(popStack());
    	}
    }

    public void sync() {
		if (sync != null) {
			sync.release();
		}
    }

    public boolean waitForSync(int millis) {
    	while (true) {
	    	try {
	    		int availablePermits = sync.drainPermits();
	    		if (availablePermits > 0) {
	    			break;
	    		}

    			if (sync.tryAcquire(millis, TimeUnit.MILLISECONDS)) {
    				break;
    			}
				return false;
			} catch (InterruptedException e) {
				// Ignore exception and retry again
				sceGe_user.log.debug(String.format("PspGeList waitForSync %s", e));
			}
    	}

    	return true;
    }

    public void setStallAddr(int stall_addr) {
    	stall_addr &= pcAddressMask;
    	if (this.stall_addr != stall_addr) {
    		this.stall_addr = stall_addr;
			ExternalGE.onStallAddrUpdated(this);
    		sync();
    	}
    }

    public synchronized void setStallAddr(int stall_addr, IMemoryReader baseMemoryReader, int startAddress, int endAddress) {
    	// Both the stall address and the base memory reader need to be set at the same
    	// time in a synchronized call in order to avoid any race condition
    	// with the GUI thread (VideoEngine).
    	setStallAddr(stall_addr);

    	this.baseMemoryReader = baseMemoryReader;
		this.baseMemoryReaderStartAddress = startAddress;
		this.baseMemoryReaderEndAddress = endAddress;
		resetMemoryReader(pc);
    }

    public int getStallAddr() {
    	return stall_addr;
    }

    public boolean isStallReached() {
    	return pc == stall_addr && stall_addr != 0;
    }

    public boolean hasStallAddr() {
    	return stall_addr != 0;
    }

    public boolean isStalledAtStart() {
    	return isStallReached() && pc == list_addr;
    }

    public void startList() {
    	paused = false;
    	ExternalGE.onGeStartList(this);
    	if (ExternalGE.isActive()) {
    		ExternalGE.startList(this);
    	} else {
    		videoEngine.pushDrawList(this);
    	}
    	sync();
    }

    public void startListHead() {
        paused = false;
        ExternalGE.onGeStartList(this);
        if (ExternalGE.isActive()) {
        	ExternalGE.startListHead(this);
        } else {
        	videoEngine.pushDrawListHead(this);
        }
    }

    public void pauseList() {
    	paused = true;
    }

    public void restartList() {
    	paused = false;
    	restarted = true;
    	sync();
		ExternalGE.onRestartList(this);
    }

    public void clearRestart() {
    	restarted = false;
    }

    public void clearPaused() {
    	paused = false;
    }

    public boolean isRestarted() {
    	return restarted;
    }

    public boolean isPaused() {
    	return paused;
    }

    public boolean isFinished() {
    	return finished;
    }

    public boolean isEnded() {
        return ended;
    }

    public void finishList() {
    	finished = true;
    	ExternalGE.onGeFinishList(this);
    }

    public void endList() {
        if (isFinished()) {
            ended = true;
        } else {
            ended = false;
        }
    }

    public boolean isDone() {
    	return status == PSP_GE_LIST_DONE || status == PSP_GE_LIST_CANCEL_DONE;
    }

	public boolean isReset() {
		return reset;
	}

	public boolean isDrawing() {
		return status == PSP_GE_LIST_DRAWING;
	}

	private void resetMemoryReader(int oldPc) {
		if (pc >= baseMemoryReaderStartAddress && pc < baseMemoryReaderEndAddress) {
			memoryReader = baseMemoryReader;
			memoryReader.skip((pc - baseMemoryReader.getCurrentAddress()) >> 2);
		} else if (memoryReader == null || memoryReader == baseMemoryReader || pc < oldPc) {
			memoryReader = MemoryReader.getMemoryReader(pc, 4);
		} else if (oldPc < MemoryMap.START_RAM && pc >= MemoryMap.START_RAM) {
			// Jumping from VRAM to RAM
			memoryReader = MemoryReader.getMemoryReader(pc, 4);
		} else {
			memoryReader.skip((pc - oldPc) >> 2);
		}
	}

	public synchronized void setMemoryReader(IMemoryReader memoryReader) {
		this.memoryReader = memoryReader;
	}

	public boolean hasBaseMemoryReader() {
		return baseMemoryReader != null;
	}

	public synchronized int readNextInstruction() {
		pc += 4;
		return memoryReader.readNext();
	}

	public synchronized int readPreviousInstruction() {
		memoryReader.skip(-2);
		int previousInstruction = memoryReader.readNext();
		memoryReader.skip(1);

		return previousInstruction;
	}

	public synchronized void undoRead() {
		undoRead(1);
	}

	public synchronized void undoRead(int n) {
		memoryReader.skip(-n);
	}

	public int getSaveContextAddr() {
		return saveContextAddr;
	}

	public void setSaveContextAddr(int saveContextAddr) {
		this.saveContextAddr = saveContextAddr;
	}

	public boolean hasSaveContextAddr() {
		return saveContextAddr != 0;
	}

	public boolean isInUse(int listAddr, int stackAddr) {
		if (list_addr == listAddr) {
			return true;
		}
		if (stackAddr != 0 && this.stackAddr == stackAddr) {
			return true;
		}

		return false;
	}

	public int getSyncStatus() {
		// Return the status PSP_GE_LIST_STALL_REACHED only when the stall address is reached.
		// I.e. return PSP_GE_LIST_DRAWING when the stall address has been recently updated
		// but the list processing has not yet been resumed and the status is still left
		// at the value PSP_GE_LIST_STALL_REACHED.
		if (status == PSP_GE_LIST_STALL_REACHED) {
			if (!isStallReached()) {
				return PSP_GE_LIST_DRAWING;
			}
		}

		return status;
	}

	@Override
	public String toString() {
		return String.format("PspGeList[id=0x%X, status=%s, list=0x%08X, pc=0x%08X, stall=0x%08X, cbid=0x%X, ended=%b, finished=%b, paused=%b, restarted=%b, reset=%b]", id, PSP_GE_LIST_STRINGS[status], list_addr, pc, stall_addr, cbid, ended, finished, paused, restarted, reset);
	}
}