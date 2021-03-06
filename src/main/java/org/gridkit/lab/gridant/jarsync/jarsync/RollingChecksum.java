/* RollingChecksum: interface to a "rolling" checksum.
   Copyright (C) 2003, 2007  Casey Marshall <rsdio@metastatic.org>

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
version.

ALTERNATIVELY, Jarsync may be licensed under the Apache License,
Version 2.0 (the "License"); you may not use this file except in
compliance with the License.  You may obtain a copy of the License at

  http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or
implied.  See the License for the specific language governing
permissions and limitations under the License.

If you modify Jarsync, you may extend this same choice of license for
your library, but you are not obligated to do so. If you do not offer
the same license terms, delete the license terms that your library is
NOT licensed under.  */

package org.gridkit.lab.gridant.jarsync.jarsync;

/**
 * A general interface for 32-bit checksums that have the "rolling" property.
 * 
 * @author Casey Marshall
 */
interface RollingChecksum extends Cloneable, java.io.Serializable {

    /**
     * Returns the currently-computed 32-bit checksum.
     * 
     * @return The checksum.
     */
    int getValue();

    /**
     * Resets the internal state of the checksum, so it may be re-used later.
     */
    void reset();

    /**
     * Update the checksum with a single byte. This is where the "rolling"
     * method is used.
     * 
     * @param bt
     *            The next byte.
     */
    void roll(byte bt);

    /**
     * Update the checksum by simply "trimming" the least-recently-updated byte
     * from the internal state. Most, but not all, checksums can support this.
     */
    void trim();

    /**
     * Replaces the current internal state with entirely new data.
     * 
     * @param buf
     *            The bytes to checksum.
     * @param offset
     *            The offset into <code>buf</code> to start reading.
     * @param length
     *            The number of bytes to update.
     */
    void check(byte[] buf, int offset, int length);

    /**
     * Copies this checksum instance into a new instance. This method should be
     * optional, and only implemented if the class implements the
     * {@link java.lang.Cloneable} interface.
     * 
     * @return A clone of this instance.
     */
    Object clone();

    /**
     * Tests if a particular checksum is equal to this checksum. This means that
     * the other object is an instance of this class, and its internal state
     * equals this checksum's internal state.
     * 
     * @param o
     *            The object to test.
     * @return <code>true</code> if this checksum equals the other checksum.
     */
    boolean equals(Object o);
}
