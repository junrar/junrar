/*
 * Copyright (c) 2007 innoSysTec (R) GmbH, Germany. All rights reserved.
 * Original author: Edmund Wagner
 * Creation date: 31.05.2007
 *
 * Source: $HeadURL$
 * Last changed: $LastChangedDate$
 *
 * the unrar licence applies to all junrar source and binary distributions
 * you are not allowed to use this source to re-create the RAR compression algorithm
 *
 * Here some html entities which can be used for escaping javadoc tags:
 * "&":  "&#038;" or "&amp;"
 * "<":  "&#060;" or "&lt;"
 * ">":  "&#062;" or "&gt;"
 * "@":  "&#064;"
 */
package com.github.junrar.unpack.vm;

/**
 * DOCUMENT ME
 *
 * @author $LastChangedBy$
 * @version $LastChangedRevision$
 */
public enum VMCommands {
    VM_MOV(0), VM_CMP(1), VM_ADD(2), VM_SUB(3), VM_JZ(4), VM_JNZ(5), VM_INC(6),
    VM_DEC(7), VM_JMP(8), VM_XOR(9), VM_AND(10), VM_OR(11), VM_TEST(12),
    VM_JS(13), VM_JNS(14), VM_JB(15), VM_JBE(16), VM_JA(17), VM_JAE(18),
    VM_PUSH(19), VM_POP(20), VM_CALL(21), VM_RET(22), VM_NOT(23), VM_SHL(24),
    VM_SHR(25), VM_SAR(26), VM_NEG(27), VM_PUSHA(28), VM_POPA(29), VM_PUSHF(30),
    VM_POPF(31), VM_MOVZX(32), VM_MOVSX(33), VM_XCHG(34), VM_MUL(35), VM_DIV(36),
    VM_ADC(37), VM_SBB(38), VM_PRINT(39),

    // #ifdef VM_OPTIMIZE
    VM_MOVB(40), VM_MOVD(41), VM_CMPB(42), VM_CMPD(43),

    VM_ADDB(44), VM_ADDD(45), VM_SUBB(46), VM_SUBD(47), VM_INCB(48), VM_INCD(49),
    VM_DECB(50), VM_DECD(51), VM_NEGB(52), VM_NEGD(53),
    // #endif*/

    VM_STANDARD(54);

    private final int vmCommand;

    VMCommands(final int vmCommand) {
        this.vmCommand = vmCommand;
    }

    public int getVMCommand() {
        return vmCommand;
    }

    public boolean equals(final int vmCommand) {
        return this.vmCommand == vmCommand;
    }

    public static VMCommands findVMCommand(final int vmCommand) {
        for (final VMCommands e : values()) {
            if (e.equals(vmCommand)) {
                return e;
            }
        }
        return null;
    }
}
