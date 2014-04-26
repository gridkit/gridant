/* Util: Basic utility functions.
   Copyright (C) 2001,2002  The Free Software Foundation, Inc.
   Copyright (C) 2003, 2007  Casey Marshall <rsdio@metastatic.org>

Parts of this file (the toHexString methods) are derived from the
gnu.crypto.util.Util class in GNU Crypto.

This file is a part of Jarsync.

Jarsync is free software; you can redistribute it and/or modify it
under the terms of the GNU General Public License as published by the
Free Software Foundation; either version 2 of the License, or (at your
option) any later version.

Jarsync is distributed in the hope that it will be useful, but WITHOUT
ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
for more details.

You should have received a copy of the GNU General Public License
along with Jarsync; if not, write to the Free Software Foundation,
Inc., 51 Franklin Street, Fifth Floor, Boston, MA 02110-1301 USA.

Linking Jarsync statically or dynamically with other modules is making
a combined work based on Jarsync.  Thus, the terms and conditions of
the GNU General Public License cover the whole combination.

As a special exception, the copyright holders of Jarsync give you
permission to link Jarsync with independent modules to produce an
executable, regardless of the license terms of these independent
modules, and to copy and distribute the resulting executable under
terms of your choice, provided that you also meet, for each linked
independent module, the terms and conditions of the license of that
module.  An independent module is a module which is not derived from
or based on Jarsync.  If you modify Jarsync, you may extend this
exception to your version of it, but you are not obligated to do so.
If you do not wish to do so, delete this exception statement from your
version.  */

package org.gridkit.lab.gridant.jarsync.jarsync;


/**
 * A number of useful, static methods.
 */
final class Util {

    /** Hexadecimal digits. */
    private static final char[] HEX_DIGITS = { '0', '1', '2', '3', '4', '5', '6', '7', '8', '9', 'a', 'b', 'c', 'd', 'e', 'f' };

    /**
     * Convert a byte array to a big-endian ordered hexadecimal string.
     * 
     * @param b
     *            The bytes to convert.
     * @return A hexadecimal representation to <tt>b</tt>.
     */
    public static String toHexString(byte[] b) {
        return toHexString(b, 0, b.length);
    }

    /**
     * Convert a byte array to a big-endian ordered hexadecimal string.
     * 
     * @param b
     *            The bytes to convert.
     * @return A hexadecimal representation to <tt>b</tt>.
     */
    public static String toHexString(byte[] b, int off, int len) {
        char[] buf = new char[len * 2];
        for (int i = 0, j = 0, k; i < len;) {
            k = b[off + i++];
            buf[j++] = HEX_DIGITS[(k >>> 4) & 0x0F];
            buf[j++] = HEX_DIGITS[k & 0x0F];
        }
        return new String(buf);
    }
}
