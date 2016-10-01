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
package jpcsp.HLE;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

/**
* Annotation for a PspString type, that indicates the maximum length of the string. 
*/
@Retention(RetentionPolicy.RUNTIME)
public @interface StringInfo {
	/**
	 * Maximum length that the will be read from the memory address. 
	 */
	public int maxLength() default -1;

	/**
	 * Encoding of the string.
	 */
	public String encoding() default "UTF-8";
}
