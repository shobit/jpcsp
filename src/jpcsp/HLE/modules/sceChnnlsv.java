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

import org.apache.log4j.Logger;

import jpcsp.HLE.BufferInfo;
import jpcsp.HLE.BufferInfo.LengthInfo;
import jpcsp.HLE.BufferInfo.Usage;
import jpcsp.HLE.CanBeNull;
import jpcsp.HLE.HLEFunction;
import jpcsp.HLE.HLEModule;
import jpcsp.HLE.HLEUnimplemented;
import jpcsp.HLE.Modules;
import jpcsp.HLE.TPointer;
import jpcsp.HLE.TPointer8;
import jpcsp.crypto.CryptoEngine;
import jpcsp.crypto.SAVEDATA;
import jpcsp.util.Utilities;

public class sceChnnlsv extends HLEModule{
    public static Logger log = Modules.getLogger("sceChnnlsv");
    private CryptoEngine crypto = new CryptoEngine();

    /**
     * Initialize the SceSdCtx2 struct and set the mode.
     *
     * @param ctx Pointer to the SceSdCtx2 struct
     * @param mode One of the modes whichs sets the scramble key for kirk.
     *
     * @return SCE_ERROR_OK on initialization success.
     * @return SCE_CHNNLSV_ERROR_ILLEGAL_ADDR if ctx cannot be accessed from the current context.
     *
     */
    @HLEFunction(nid = 0xE7833020, version = 150)
    public int sceSdSetIndex(@BufferInfo(lengthInfo=LengthInfo.fixedLength, length=40, usage=Usage.out) TPointer ctx2Addr, int mode) {
    	SAVEDATA.SD_Ctx1 ctx = new SAVEDATA.SD_Ctx1();

    	int result = crypto.getSAVEDATAEngine().hleSdSetIndex(ctx, mode);

    	ctx.write(ctx2Addr);

    	return result;
    }

    /**
     * Generates a hash storing the result into ctx->data and updates ctx->key
     *
     * @param ctx Pointer to the SceSdCtx2 struct
     * @param data Pointer to the data used in hash generation
     * @param size The size of the data used for hash generation
     *
     * @return SCE_ERROR_OK on success
     * @return SCE_CHNNLSV_ERROR_ILLEGAL_ADDR if ctx/data cannot be accessed from the current context.
     * @return SCE_CHNNLSV_ERROR_SEMA_ERROR wait/signal sema error
     * @return SCE_CHNNLSV_ERROR_ILLEGAL_SIZE if ctx->size > 16
     *
     */
    @HLEUnimplemented
    @HLEFunction(nid = 0xF21A1FCA, version = 150)
    public int sceSdRemoveValue(@BufferInfo(lengthInfo=LengthInfo.fixedLength, length=40, usage=Usage.inout) TPointer ctx2Addr, @BufferInfo(lengthInfo=LengthInfo.nextParameter, usage=Usage.in) TPointer data, int size) {
    	SAVEDATA.SD_Ctx1 ctx = new SAVEDATA.SD_Ctx1();
    	ctx.read(ctx2Addr);

    	byte[] bytes = new byte[size];
    	Utilities.readBytes(data.getAddress(), size, bytes, 0);
    	int result = crypto.getSAVEDATAEngine().hleSdRemoveValue(ctx, bytes, size);

    	ctx.write(ctx2Addr);

    	return result;
    }

    /**
     * Generates a hash based on the context collected by sceSdRemoveValue,
     * the results of which are stored into the SAVEDATA_PARAMS field of PARAM.SFO
     *
     * @param ctx Pointer to the SceSdCtx2 struct
     * @param hash The end result of the hash generated by this function is stored here
     * @param key If provided, this key will also be used in the encryption process
     *
     * @return SCE_ERROR_OK on success
     * @return SCE_CHNNLSV_ERROR_ILLEGAL_ADDR if ctx/hash/key cannot be accessed from the current context.
     * @return SCE_CHNNLSV_ERROR_SEMA_ERROR wait/signal sema error
     * @return SCE_CHNNLSV_ERROR_ILLEGAL_SIZE if ctx->size > 16
     *
     */
    @HLEUnimplemented
    @HLEFunction(nid = 0xC4C494F8, version = 150)
    public int sceSdGetLastIndex(@BufferInfo(lengthInfo=LengthInfo.fixedLength, length=40, usage=Usage.inout) TPointer ctx2Addr, @CanBeNull @BufferInfo(lengthInfo=LengthInfo.fixedLength, length=16, usage=Usage.out) TPointer8 hash, @CanBeNull @BufferInfo(lengthInfo=LengthInfo.fixedLength, length=16, usage=Usage.out) TPointer8 key) {
    	SAVEDATA.SD_Ctx1 ctx = new SAVEDATA.SD_Ctx1();
    	ctx.read(ctx2Addr);

    	byte[] hashBytes = hash.isNull() ? null : new byte[16];
    	byte[] keyBytes = key.isNull() ? null : new byte[16];

    	int result = crypto.getSAVEDATAEngine().hleSdGetLastIndex(ctx, hashBytes, keyBytes);

    	if (hashBytes != null) {
    		Utilities.writeBytes(hash.getAddress(), hashBytes.length, hashBytes, 0);
    	}
    	if (keyBytes != null) {
    		Utilities.writeBytes(key.getAddress(), keyBytes.length, keyBytes, 0);
    	}

    	ctx.write(ctx2Addr);

    	return result;
    }
}
