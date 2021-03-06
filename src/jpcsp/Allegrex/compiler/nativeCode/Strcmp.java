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
package jpcsp.Allegrex.compiler.nativeCode;

import jpcsp.Emulator;
import jpcsp.Memory;
import jpcsp.util.Utilities;

/**
 * @author gid15
 *
 */
public class Strcmp extends AbstractNativeCodeSequence {
	static public void call() {
		if (!Memory.isAddressGood(getGprA0())) {
			getMemory().invalidMemoryAddress(getGprA0(), "strcmp", Emulator.EMU_STATUS_MEM_READ);
			return;
		}
		if (!Memory.isAddressGood(getGprA1())) {
			getMemory().invalidMemoryAddress(getGprA1(), "strcmp", Emulator.EMU_STATUS_MEM_READ);
			return;
		}

		if (log.isDebugEnabled()) {
			log.debug(String.format("strcmp src1=%s, src2=%s", Utilities.getMemoryDump(getGprA0(), getStrlen(getGprA0())), Utilities.getMemoryDump(getGprA1(), getStrlen(getGprA1()))));
		}

		setGprV0(strcmp(getGprA0(), getGprA1()));
	}

	static public void call(int valueEqual, int valueLower, int valueHigher) {
		if (!Memory.isAddressGood(getGprA0())) {
			getMemory().invalidMemoryAddress(getGprA0(), "strcmp", Emulator.EMU_STATUS_MEM_READ);
			return;
		}
		if (!Memory.isAddressGood(getGprA1())) {
			getMemory().invalidMemoryAddress(getGprA1(), "strcmp", Emulator.EMU_STATUS_MEM_READ);
			return;
		}

		int cmp = strcmp(getGprA0(), getGprA1());
		if (cmp < 0) {
			setGprV0(valueLower);
		} else if (cmp > 0) {
			setGprV0(valueHigher);
		} else {
			setGprV0(valueEqual);
		}
	}
}
