# junrar ⇄ unrar 3.7.3 translation catalog — PPMd, RarVM, Crypto, CRC, IO layers

Baseline: unrar **3.7.3** = commit `2e71167` in `~/git/unrar` (read via `git show 2e71167:<file>`).
Java: `~/git/junrar` master (v7.6.x era, after the 2026 CRC swap `5270d235`).
Purpose: pattern manual for porting newer unrar (RAR5) code into junrar. Each pattern gives the
C++ construct, the Java translation, and a RULE for future translators.

Path shorthand: `J:` = `~/git/junrar/src/main/java/com/github/junrar/`, `C:` = file at unrar
commit `2e71167`.

---

## 1. PPMd pointer emulation — the flagship pattern

### 1.1 One heap, `int` = pointer

C++: `SubAllocator` owns one `rarmalloc`'d region and every PPMd structure lives inside it;
pointers are raw `byte*` / `PPM_CONTEXT*` / `STATE*` into that region
(`C:suballoc.hpp:65-83` — `byte *HeapStart,*LoUnit,*HiUnit; byte *pText,*UnitsStart,*HeapEnd,*FakeUnitsStart`).

Java: the heap is a single `byte[] heap` (`J:unpack/ppm/SubAllocator.java:54`) and **every C++
pointer becomes an `int` offset into that array**. The same fields exist 1:1 as ints:

- `J:unpack/ppm/SubAllocator.java:47` `private int heapStart, loUnit, hiUnit;` (comment keeps
  the C++ declaration: `// byte *HeapStart,*LoUnit, *HiUnit;`)
- `J:...SubAllocator.java:52` `private int pText, unitsStart, heapEnd, fakeUnitsStart;`

**NULL is offset 0**, made unambiguous by reserving heap byte 0:
`startSubAllocator` allocates `1 + allocSize + 4*N_INDEXES + RarMemBlock.size` and sets
`heapStart = 1` (`J:...SubAllocator.java:141-148`, comment `// 1+ for null pointer`). Every
C++ `ptr==NULL` test became `addr == 0` (e.g. `J:unpack/ppm/ModelPPM.java:245`
`while (foundState.getAddress() == 0)`; allocation failure returns `0` at
`SubAllocator.java:235`).

RULE: *Pointer-heavy C++ translates to one `byte[]` + `int` offsets. Reserve offset 0 as NULL
(start real data at 1). Every pointer comparison (`<= pText`, `> HeapEnd`) works unchanged on
ints — see the corruption guards in `ModelPPM.decodeChar()` (`J:...ModelPPM.java:228-236`)
mirroring `C:model.cpp` `DecodeChar`.*

### 1.2 Structs = flyweight cursor objects (`Pointer` subclasses)

C++ structs stored *in* the heap (`C:model.hpp:45-79`, `#pragma pack(1)`):

```cpp
struct STATE { byte Symbol; byte Freq; PPM_CONTEXT* Successor; };          // 6 bytes packed
struct FreqData { ushort SummFreq; STATE _PACK_ATTR * Stats; };            // 6 bytes
struct PPM_CONTEXT { ushort NumStats; union { FreqData U; STATE OneState; }; PPM_CONTEXT* Suffix; }; // 12
struct RAR_MEM_BLK { ushort Stamp, NU; RAR_MEM_BLK *next, *prev; };        // 12 (suballoc.hpp:23-37)
struct RAR_NODE { RAR_NODE* next; };                                       // 4  (suballoc.hpp:48-51)
```

Java: abstract `Pointer` = `{byte[] mem; int pos}` (`J:unpack/ppm/Pointer.java:26-56`, javadoc:
"Simulates Pointers on a single mem block as a byte[]"). Each struct becomes a `Pointer`
subclass that owns **no data**, only typed accessors that read/write the heap at fixed field
offsets via `io/Raw` little-endian helpers:

- `State` (`J:unpack/ppm/State.java:28-92`): `size = 6`; `getSymbol()` = `mem[pos]&0xff`,
  `getFreq()` = `mem[pos+1]&0xff`, `getSuccessor()` = `Raw.readIntLittleEndian(mem, pos+2)`.
- `FreqData` (`J:unpack/ppm/FreqData.java:28-70`): `size = 6`; SummFreq = ushort at `pos`,
  Stats pointer = int at `pos+2`.
- `PPMContext` (`J:unpack/ppm/PPMContext.java:28-125`): `size = 2 + max(FreqData.size,
  State.size) + 4 = 12`; NumStats ushort at `pos`, Suffix int at `pos+8`.
- `RarMemBlock` (`J:unpack/ppm/RarMemBlock.java:30-124`): `size = 12`; Stamp/NU ushorts at
  `pos`/`pos+2`, next/prev ints at `pos+4`/`pos+8`; `insertAt`/`remove` re-implement the
  intrusive doubly-linked list of `C:suballoc.hpp:27-36` through the accessors.
- `RarNode` (`J:unpack/ppm/RarNode.java:30-55`): `size = 4`, single next pointer.

Pointer arithmetic maps mechanically:

| C++ | Java |
|---|---|
| `p++` (STATE*) | `p.incAddress()` = `setAddress(pos + State.size)` (`State.java:89-92`) |
| `p--` | `p.decAddress()` (`State.java:84-87`) |
| `*p = *q` (struct copy) | `p.setValues(q)` = `System.arraycopy(q.mem, q.pos, mem, pos, size)` (`State.java:80-82`) |
| `_PPMD_SWAP(*p1,*p2)` (`C:model.hpp:98-99`) | `State.ppmdSwap(p1,p2)` byte-loop swap (`State.java:94-101`) |
| `(RAR_NODE*)p` cast | `tempRarNode.setAddress(p)` — retype by pointing a differently-shaped cursor at the same offset (`SubAllocator.java:74-79`) |
| `memcpy(ptr,OldPtr,U2B(OldNU))` | `System.arraycopy(heap, oldPtr, heap, ptr, U2B(oldNU))` (`SubAllocator.java:276`, `296`) |
| `STATE* ps[MAX_O]; **pps` | `int[] ps` array of addresses + `int pps` index (`ModelPPM.java:88`, `393-431`; `PPMContext.java:55`, `303-344`) |

The C++ union (`PPM_CONTEXT.U` / `OneState`) becomes **two cursor objects aimed at the same
offset**: `PPMContext.setAddress()` cascades `oneState.setAddress(pos+2)` and
`freqData.setAddress(pos+2)` (`J:...PPMContext.java:120-125`); `unionSize =
Math.max(FreqData.size, State.size)` (`PPMContext.java:30`). Which interpretation is valid is
decided exactly as in C++ — by `NumStats == 1`.

RULE: *One flyweight class per C++ struct: `static final int size`, accessors at hard-coded
field offsets, `inc/decAddress` for pointer steps, `setValues` for struct copy, a second
cursor at the same address for unions/casts. Never materialize structs as Java objects with
fields — the heap byte layout IS the data structure (free-list nodes are written INTO freed
blocks, `SubAllocator.insertNode()`; PPMd reads text bytes through the same heap as context
structs).*

### 1.3 Stack temporaries and `STATE&` references

C++ freely does `STATE fs = *FoundState;` (value copy on the stack, `C:model.cpp:231` region)
and passes `STATE&`. Java splits the concept:

- **`StateRef`** (`J:unpack/ppm/StateRef.java:27-78`) = a *detached value copy* with plain int
  fields (symbol/freq/successor) — used where C++ copies a STATE by value or passes `STATE&`
  that outlives heap edits (`ModelPPM.updateModel()` `fs`, `ModelPPM.java:479-480`;
  `createChild(..., StateRef firstState)` `PPMContext.java:134`).
- **Pre-allocated cursor pools** — because cursors are cheap but not free, hot paths reuse
  `private final State tempState1..5`, `tempPPMContext1..4` fields, re-aimed via
  `init(heap)`/`setAddress()` (`ModelPPM.java:78-88`, `PPMContext.java:49-55`). This is the
  translation of C++ *local pointer variables*.

RULE: *A C++ struct value copy = a dumb value class (`XxxRef`); a C++ local pointer = a reused
cursor field. Distinguish them at porting time: mixing the two (mutating a cursor where C++
had a value snapshot) is the classic bug source in this style of port.*

### 1.4 Allocator internals kept literal

`UNIT_SIZE = Math.max(PPMContext.size, RarMemBlock.size)` = 12 and `FIXED_UNIT_SIZE = 12`
(`J:...SubAllocator.java:34-37`) mirror `C:model.hpp:89-90`
(`UNIT_SIZE=Max(sizeof(PPM_CONTEXT),sizeof(RAR_MEM_BLK))`). Because Java "sizeof" is the
*packed* size by construction, both are 12 and the `FIXED_UNIT_SIZE`→`UNIT_SIZE` scaling in
`startSubAllocator`/`initSubAllocator` (`SubAllocator.java:137`, `347-356` vs
`C:suballoc.cpp:83`, `103-114`) is a no-op numerically — but the code keeps the two constants
separate exactly as C++ does, so a future struct-size change stays correct.

The free lists (`RAR_NODE FreeList[N_INDEXES]`, a C++ member array) must themselves have heap
addresses in Java, since list nodes are addressed by offset. junrar **appends them to the same
heap**: `realAllocSize = 1 + allocSize + 4*N_INDEXES` and `freeList[i].setAddress(freeListPos +
i*RarNode.size)` (`SubAllocator.java:141-159`, comment "adding space for freelist (needed for
poiters)"). Same for the temporary `RAR_MEM_BLK s0` local in `GlueFreeBlocks`
(`C:suballoc.cpp`): Java reserves one extra `RarMemBlock.size` slot at `tempMemBlockPos`
(`SubAllocator.java:143-144`) and aims `tempRarMemBlock1` at it (`glueFreeBlocks()`,
`SubAllocator.java:170-214`).

RULE: *Any C++ object whose ADDRESS participates in the pointer graph (list heads, sentinel
nodes, stack locals linked into lists) must be given space INSIDE the emulated heap. Grep the
C++ for `&member` / `&local` used as a node pointer before sizing the array.*

### 1.5 Validation harness

`J:unpack/ppm/AnalyzeHeapDump.java` byte-compares a C++ heap dump (`heapdumpc`) with a Java
heap dump (`heapdumpj`); `SubAllocator` retains the (commented) `dumpHeap()` producer
(`SubAllocator.java:387-402`). The original porter validated the emulation by diffing the two
allocator heaps byte-for-byte after identical inputs.

RULE: *For pointer-emulation ports, build the byte-diff harness first: same input archive, dump
both heaps, assert identity. It converts "does my pointer emulation drift?" into a mechanical
check. Little-endian layout in the Java heap (via `Raw.*LittleEndian`) is what makes dumps
comparable with an x86 C++ build.*

---

## 2. Struct memory layout / sizeof discipline

- C++ relies on `#pragma pack(1)` + `_PACK_ATTR` (`C:suballoc.hpp:13-21`, `C:model.hpp:12-14`)
  so that `sizeof(STATE)=6`, `sizeof(PPM_CONTEXT)=12`. Java encodes the packed layout directly
  in accessor offsets; the `static final int size` constant on each flyweight class is the
  sizeof (`State.java:30`, `FreqData.java:30`, `PPMContext.java:32`, `RarMemBlock.java:32`,
  `RarNode.java:33`).
- Field widths: C++ `ushort` → Java `int` + `& 0xffff` on the read path
  (`PPMContext.getNumStats()` `PPMContext.java:80-85`; `RarMemBlock.getNU()`
  `RarMemBlock.java:80-85`); C++ `byte` → `mem[pos] & 0xff` (`State.getSymbol()`).
- All multi-byte heap fields are **little-endian** via `io/Raw`
  (`J:io/Raw.java:102-139` readers, `236-296` writers), matching the packed x86 layout.
- Read-modify-write helpers exist where C++ does `field += x` on a packed ushort:
  `Raw.incShortLittleEndian` (`Raw.java:250-256`) backs `FreqData.incSummFreq`
  (`FreqData.java:56-58`).
- SEE2 contexts (`C:model.hpp:16-39`) are NOT heap-resident (they live in `ModelPPM`, not in
  the suballocator), so junrar promotes them to ordinary Java objects
  (`J:unpack/ppm/SEE2Context.java`, `ModelPPM.java:48-50`) — no byte emulation needed.
- Caching quirk: several accessors keep a shadow field updated on read
  (`if (mem != null) numStats = Raw.read...` then return the field, `PPMContext.java:80-85`) so
  the same class can operate detached (mem==null) in a few construction paths. Modern ports
  should skip the shadow fields — they blur where truth lives.

RULE: *Translate `sizeof` into an explicit `size` constant per struct and derive every field
offset from the packed layout, not from Java conventions. Only heap-resident structs get the
byte[] treatment; container-member structs become normal objects.*

---

## 3. RarVM translation

### 3.1 Shape

C++ `class RarVM : private BitInput` (`C:rarvm.hpp`) → `public class RarVM extends BitInput`
(`J:unpack/vm/RarVM.java:33`). Constants identical: `VM_MEMSIZE=0x40000`,
`VM_MEMMASK=VM_MEMSIZE-1`, `VM_GLOBALMEMADDR=0x3C000`, `VM_GLOBALMEMSIZE=0x2000`,
`VM_FIXEDGLOBALSIZE=64` (`RarVM.java:35-43`). VM memory is `mem = new byte[VM_MEMSIZE + 4]`
(`RarVM.java:67`) — the `+4` slop absorbs 4-byte accesses at the top edge instead of C++'s
unchecked pointer reads.

C++ `VM_PreparedProgram { Array<byte> GlobalData, StaticData; uint InitR[7]; ... }`
(`C:rarvm.hpp:68-70`) → `J:unpack/vm/VMPreparedProgram.java:31-41` with
`List<VMPreparedCommand>` for code, `Vector<Byte>` for global/static data (boxing — a wart,
not a pattern), `int[] InitR = new int[7]`. Opcode/flag tables `C:rarvmtbl.cpp` →
`J:unpack/vm/VMCmdFlags.java` (`VM_CmdFlags[]` byte table), enums `VMCommands`, `VMFlags`,
`VMOpType`, `VMStandardFilters` replace C++ raw enums, each carrying its int and a
`findXxx(int)` reverse lookup (`VMCommands.findVMCommand`).

### 3.2 Interpreter loop

C++ `ExecuteCode` giant `switch` inside `while(1)` with `SET_IP`+`continue` for jumps and a
25 000 000 op budget (`C:rarvm.cpp:170-525`; `#define SET_IP` at 165-175, `MaxOpCount` local
at 178) → same structure verbatim in Java: `ExecuteCode` (`RarVM.java:220-630`), `setIP()`
helper returning false on budget exhaustion (`RarVM.java:207-218`), `maxOpCount = 25000000`
reset per run (`RarVM.java:223`), `IP++; --maxOpCount;` at loop tail (`RarVM.java:627-628`).
On abnormal exit, `execute()` stamps a `VM_RET` over instruction 0 and continues
(`RarVM.java:174-176` = C++ semantics).

Operand access: C++ caches a `uint* Addr` per operand; Java `getOperand()` recomputes
`(offset+base) & VM_MEMMASK` and reads LE (`RarVM.java:133-143`, cf. `C:rarvm.cpp:121`).
Every store to VM memory through the stack pointer masks with `& VM_MEMMASK`
(`RarVM.java:467-477` PUSH/POP/CALL = `C:rarvm.cpp:379-390`) — the VM's only memory-safety
mechanism, preserved exactly.

### 3.3 Endianness contract

C++ comment (`C:rarvm.cpp:25-28`): "Only Mem data are always low endian regardless of machine
architecture... VM registers have endianness of host machine." `GetValue/SetValue`
(`C:rarvm.cpp:31-99`) special-case `IS_VM_MEM(Addr)`. Java keeps the same split:
`getValue(byteMode, mem, offset)` uses **little-endian for VM memory** and **big-endian for
any other array** (`RarVM.java:75-118`), with `isVMMem()` = reference equality against the VM
buffer (`RarVM.java:71-73`). The "other array" case exists because C++ sometimes points
GET_VALUE at host structures (e.g. `Cmd->Op1.Data`); junrar's big-endian branch reproduces the
byte-array view a big-endian host would need. In practice junrar always passes the VM `mem`,
so the BE branch is near-dead — but keep the shape when porting.

`setLowEndianValue` (`RarVM.java:120-132`) is the explicit-LE store used by callers outside
the VM (filter parameter blocks), including a `Vector<Byte>` overload for `GlobalData`.

### 3.4 Bytecode preparation

`prepare()` (`RarVM.java:632-761`) is a line-by-line port of `C:rarvm.cpp:530-620`: XOR
checksum of code bytes vs byte 0, `IsStandardFilter` check, 4/6-bit opcode decode, per-opcode
byte-mode bit, operand decoding via `decodeArg` (`RarVM.java:763-801`), jump-distance
normalization table (`RarVM.java:714-731`), terminating VM_RET append, then `optimize()`
(`RarVM.java:804-861`) which rewrites MOV/CMP/ADD/... into B/D-suffixed specializations when
no later instruction consumes flags — the port of unrar's `#ifdef VM_OPTIMIZE` pass, including
the `VMCF_CHFLAGS/USEFLAGS` scan.

Variable-length ints: `ReadData(BitInput)` static helper (`RarVM.java:863-891`) ports the
`RarVM::ReadData` bit-format (2-bit tag: 4-bit / signed-byte / 16-bit / 32-bit).

### 3.5 Standard filters

C++ detects the 7 known filter programs by (length, CRC32) signature and runs a native
implementation instead of interpreting bytecode (`C:rarvm.cpp:817-831` table, `835+`
`ExecuteStandardFilter`). Java table is identical — 53/0xad576887 E8, 57/0x3cd7e57e E8E9,
120/0x3769893f ITANIUM, 29/0x0e06077d DELTA, 149/0x1c2c5dc8 RGB, 216/0xbc85e701 AUDIO,
40/0x46b9c560 UPCASE (`RarVM.java:893-913`) — computed with **`java.util.zip.CRC32`**
(`RarVM.java:903-905`).

`ExecuteStandardFilter` (`RarVM.java:916-1171`) ports each filter with `byte*` → int index
into `mem`: e.g. `SrcData=Mem, DestData=SrcData+DataSize` becomes `srcPos=0, destDataPos =
dataSize` (VMSF_RGB, `RarVM.java:1017-1062`); filter parameters arrive in registers
(`R[4]`=length, `R[6]`=file offset) and results are reported by storing block pos/size at
`VM_GLOBALMEMADDR+0x20/0x1c` (`RarVM.java:1000`, `1165-1166`) exactly as C++. Itanium
bit-surgery helpers `FilterItanium_GetBits/SetBits` port 1:1 (`RarVM.java:1173-1199`).
`R[6]` widening: `long fileOffset = R[6] & 0xFFffFFff` (`RarVM.java:921`) — uint kept in long.

### 3.6 Known fidelity bugs — do NOT copy these idioms

The interpreter contains latent mis-translations that survive because real archives almost
always hit the standard-filter fast path, not interpreted bytecode:

1. **Operator precedence in masked-long arithmetic.** Java `+`/`-` bind tighter than `&`, so
   `(long)a & 0xFFffFFff + (long)b & 0xFFffFFff` computes `a & (0xFFffFFff + b) & mask` —
   wrong. Instances: VM_ADDB/ADDD (`RarVM.java:300-312`), VM_SUBB/SUBD (`325-337`),
   VM_INC/INCB/INCD (`351-373`, where `x & (0xFFffFFff + 1)` is almost always 0), VM_DEC
   family (`375-393`), VM_MUL (`565-571`), VM_ADC/VM_SBB (`580-609`). C++ originals are plain
   `GET_VALUE(...)+GET_VALUE(...)` (`C:rarvm.cpp` VM_ADDB region).
2. **Ternary-vs-`|` precedence in flag computation.** C++
   `Flags=Result==0 ? VM_FZ:(Result>Value1)|(Result&VM_FS);` (`C:rarvm.cpp:202`). Java
   `flags = (result > value1) ? 1 : 0 | (result & VM_FS)` (`RarVM.java:250-251`) parses as
   `(result>value1) ? 1 : (0|(result&FS))` — carry set drops the sign bit; also the unsigned
   compare `Result>Value1` (uint) became a signed int compare.
3. **VM_SAR uses `>>>`** (`RarVM.java:509`) where C++ is arithmetic `((int)Value1)>>Value2`
   (`C:rarvm.cpp:411-414`) — SAR behaves as SHR.
4. **`prepare()` copies code with `inBuf[i] |= code[i]`** (`RarVM.java:636`) where C++ is
   `memcpy(InBuf,Code,...)` (`C:rarvm.cpp:535`) — correct only while the shared `BitInput`
   buffer still holds zeros at those positions; a second `prepare()` on the same RarVM can OR
   stale bytes.
5. `ReadData` case 0x4000 sign-extension: Java `data = 0xffffff00 | ((data >>> 2) & 0xff)`
   (`RarVM.java:871`) matches C++, but note it relies on int overflow semantics — fine.

RULE: *When porting C expressions that mix arithmetic and masking, parenthesize every masked
term: `((long)a & 0xFFFFFFFFL) + ((long)b & 0xFFFFFFFFL)`. Port `>>` on values the C++ casts
to `int` as Java `>>`, `uint` shifts as `>>>`. Add per-opcode unit tests — junrar's VM bugs
persist precisely because nothing exercises them. For RAR5 (which kept the same VM only for
RAR3-compat and dropped it for RAR5 filters — RAR5 has only the 6 hardcoded filter types),
prefer porting the native filter implementations and skipping the interpreter entirely, as
junrar's standard-filter path already demonstrates.*

### 3.7 BitInput

`C:getbits.hpp` (class with `InAddr/InBit`, 16-bit lookahead `getbits()` from 3 bytes) →
`J:unpack/vm/BitInput.java:26-100`, near-verbatim including names (`InitBitInput`, `addbits`,
`getbits`, `faddbits/fgetbits`, `Overflow`, `MAX_SIZE=0x8000`). Only change: explicit `&0xff`
per byte and `>>>` (`BitInput.java:60-62`). C++ method names' casing was kept
(`InitBitInput()`), an accepted style cost for greppability.

RULE: *Keep unrar's identifier names (even non-Java casing) — 1:1 greppability between the
codebases is worth more than checkstyle purity in a port of this kind.*

---

## 4. Crypto — decision: reuse JDK, don't port

C++ RAR3 crypto = `C:crypt.cpp` (`CryptData::SetCryptKeys`, lines 188-286: KDF) + a full
Rijndael implementation (`C:rijndael.cpp`, Stefanek/Gladman table AES with CBC folded into
`blockDecrypt`) + `C:sha1.cpp` (custom SHA-1 with the RAR "HandsOffHash" quirk toggle).

junrar ported **only the KDF math** and delegated all primitives to the JDK:

- `J:crypt/Rijndael.java:38-89` `buildDecipherer(password, salt)`:
  - UTF-16LE-ish expansion of the password (`rawpsw[i*2]=pwd[i]; rawpsw[i*2+1]=0`,
    `Rijndael.java:48-51`) = C++ `WideToRaw(PswW,RawPsw)` (`C:crypt.cpp:243-249`).
  - `HashRounds = 0x40000` iterations feeding `rawpsw + 3-byte LE counter` into SHA-1; every
    `HashRounds/16` rounds, byte 19 of a *partial* digest becomes one IV byte
    (`Rijndael.java:56-74` vs `C:crypt.cpp:254-270`). C++ snapshots the running hash context
    (`hash_context tempc=c; hash_final(&tempc,...)`); Java has no cloneable digest mid-stream,
    so it **re-hashes the whole accumulated ByteArrayOutputStream** — same output, O(n²) cost.
    (`digest[19]` = low-order byte of `digest[4]` in the C++ uint32[5] view.)
  - Key = first 16 digest bytes with per-uint32 byte order swapped (`Rijndael.java:78-84` =
    `AESKey[I*4+J]=(byte)(digest[I]>>(J*8))`, `C:crypt.cpp:273-275`).
  - Primitive: `Cipher.getInstance("AES/CBC/NoPadding")` + `SecretKeySpec`/`IvParameterSpec`
    (`Rijndael.java:86-88`); SHA-1 via `MessageDigest.getInstance("sha-1")`
    (`Rijndael.java:54`). **`C:rijndael.cpp` and `C:sha1.cpp` have no Java counterpart at
    all.**
- Streaming decryption is a Cipher-wrapping channel, not a port of `CryptData::DecryptBlock`:
  `J:io/RawDataIo.java:52-77` rounds each read up to the AES block size
  (`realRead = toRead + ((~toRead + 1) & 0xF)`), pushes ciphertext through `cipher.update()`,
  and pools surplus plaintext bytes in a `LinkedList<Byte>`.
- Wiring: encrypted **headers** — `Archive.readHeaders` reads the 8-byte salt before each
  header and swaps a cipher into the `RawDataIo` (`J:Archive.java:318-324`); encrypted **file
  data** — `ComprDataIO.init(FileHeader)` builds a decipherer from the file header's salt
  (`J:unpack/ComprDataIO.java:121-124`, wrapped in `InitDeciphererFailedException`).

Known skew vs C++: `password.getBytes()` uses the platform charset and assumes 1 byte/char
(`Rijndael.java:47`) — non-Latin-1 passwords diverge from unrar's wide-char handling; no
key cache (C++ caches the last 4 KDF results, `C:crypt.cpp:227-283`); no encryption
direction; no `-p` (RAR
"old" encryption, `crypt.cpp` Crypt/Decrypt13/15/20 paths) — junrar supports only the RAR3
salted AES scheme.

RULE / RAR5 guidance: *Follow this exact decision for RAR5 crypto: AES-256/CBC =
`Cipher.getInstance("AES/CBC/NoPadding")` with a 256-bit `SecretKeySpec`; the KDF =
PBKDF2-HMAC-SHA256, which the JDK ships as `SecretKeyFactory.getInstance
("PBKDF2WithHmacSHA256")` — port only RAR5's specifics on top (the 2^count iteration rule,
the derived-key trio: key / hash-key / password-check via extra PBKDF2 rounds). Port
`crypt.cpp` MATH, never `rijndael.cpp`/`sha256.cpp` PRIMITIVES. Beware the two junrar crypto
warts when reusing code: platform-charset password bytes (RAR5 mandates UTF-8 — convert
explicitly) and the O(n²) digest-recompute idiom (unnecessary under PBKDF2).*

---

## 5. CRC — decision history: port → JDK intrinsic

C++ (`C:crc.cpp:1-61`): lazily-initialized 256-entry table, reflected CRC-32 poly 0xEDB88320,
`CRC(StartCRC, addr, size)` (callers pass 0xFFFFFFFF and xor at the end themselves), plus
`OldCRC` — RAR 1.5's rotate-add 16-bit checksum.

junrar today (post commit `5270d235`, 2026-04, "perf: replace RarCRC.checkCrc with
java.util.zip.CRC32 which uses JDK intrinsics"):

- The table port (`RarCRC.checkCrc`) is **deleted**; `J:crc/RarCRC.java` retains only
  `checkOldCrc` (`RarCRC.java:52-59`), the OldCRC rotate-add port, used for RAR 1.5 archives
  via `ComprDataIO.unpWrite` (`J:unpack/ComprDataIO.java:195`).
- CRC-32 = `java.util.zip.CRC32` everywhere: running unpack/pack CRCs in `ComprDataIO`
  (`ComprDataIO.java:74-75`, `103-104`, `139-140`, `197-198`) and filter-signature detection
  in `RarVM.IsStandardFilter` (`RarVM.java:903-905`).
- **Register-convention bridge:** unrar keeps the *un-finalized* CRC register (init
  0xFFFFFFFF, final `^0xFFFFFFFF` at compare time). `java.util.zip.CRC32.getValue()` returns
  the *finalized* value. junrar reconciles by storing `unpFileCRC = (int) (~unpCrc32.getValue())`
  after every update (`ComprDataIO.java:198`) and comparing `~getUnpFileCRC() == hd.getFileCRC()`
  (`J:Archive.java:783-788`; split-volume packed-CRC check `ComprDataIO.java:158-161`).

Gaps for RAR5:

- **Blake2sp is absent** — RAR5 file checksums may be BLAKE2sp (32-byte); nothing in junrar
  provides it and the JDK has no Blake2. Options: port unrar's `blake2s.cpp` (self-contained,
  ~300 lines, tree/parallel "sp" mode over 8 lanes) or depend on Bouncy Castle
  (`Blake2sDigest` exists, but the multi-lane `sp` composition still must be hand-built).
  This is the one primitive where "reuse JDK" fails.
- RAR5 header CRC32s: same CRC32 class works; keep the `~` register convention bridge in one
  place.
- Note existing skew: junrar parses RAR3 block `headCRC` (`J:rarfile/BaseBlock.java:88,110`)
  but never verifies most block-header CRCs (only magic constants, e.g.
  `MarkHeader.java:42`, `EndArcHeader.java:51`); unrar verifies them in `arcread.cpp`. A RAR5
  port should verify header CRCs — don't inherit the omission.

RULE: *Prefer `java.util.zip.CRC32` (intrinsics) over table ports; keep unrar's raw-register
convention by storing `~getValue()` and flipping once at the comparison boundary. Document any
checksum the JDK lacks (Blake2sp) as an explicit port-or-dependency decision.*

---

## 6. SHA-1 and hashing

`C:sha1.cpp` (custom, with `handsoff` variant that avoids in-place buffer corruption for the
crypto KDF) → **not ported**; `MessageDigest.getInstance("sha-1")` (`J:crypt/Rijndael.java:54`).
The `HandsOffHash` flag is irrelevant under MessageDigest (it never mutates caller buffers).
For RAR5: SHA-256 = `MessageDigest.getInstance("SHA-256")`; PBKDF2 via `SecretKeyFactory`
(see §4). Only Blake2sp needs source-level porting (§5).

---

## 7. IO layer — what everything must route through

unrar model: `File`/`Archive` classes own a real FD; `ComprDataIO` (`C:rdwrfn.cpp`) is the
mid-layer that meters packed bytes, decrypts, spans volumes, and accumulates CRCs; `getbits`
reads from an in-memory window filled by `UnpRead`.

junrar model (names deliberately parallel):

- **`SeekableReadOnlyByteChannel`** (`J:io/SeekableReadOnlyByteChannel.java:29-82`) is the
  storage abstraction — the successor of the old `IReadOnlyAccess`. Contract:
  `getPosition()/setPosition(long)` (absolute seeks only), `read()`, `read(buf,off,count)`,
  `readFully(buf,count)`, `close()`. Implementations: `SeekableReadOnlyFile` (RandomAccessFile,
  `J:io/SeekableReadOnlyFile.java:31`), `SeekableReadOnlyInputStream` wrapping
  `RandomAccessInputStream` (`J:io/RandomAccessInputStream.java:12-16` — a memory-cache
  seekable wrapper over InputStream, JAI MemoryCacheSeekableStream lineage), so plain streams
  gain seekability at the cost of buffering.
- **`RawDataIo`** (`J:io/RawDataIo.java:7`) decorates a channel with transparent AES
  decryption once `setCipher()` is called (§4) — the read path the *header parser* uses
  (`Archive.readHeaders` reads through it; salt → cipher swap at `Archive.java:318-324`).
- **`ComprDataIO`** (`J:unpack/ComprDataIO.java`) = straight port of `C:rdwrfn.cpp`:
  `unpRead()` meters `unpPackedSize`, auto-advances volumes on `isSplitAfter()`
  (`ComprDataIO.java:150-175`), checks the packed-part CRC across volume boundaries, and
  `unpWrite()` feeds the output stream + running CRC (`ComprDataIO.java:184-203`). The
  `Volume`/`VolumeManager` pair (`J:volume/`) abstracts .partNN/.rNN chaining for both File
  and InputStream sources.
- **`Raw`** (`J:io/Raw.java`) is the endian toolkit for both header parsing (`rarfile/*`
  headers parse from byte[] with `Raw.readShortLittleEndian` etc.) and the PPMd heap (§1).
- Decompressors never touch channels: `Unpack` pulls via `unpIO.unpRead(buffer,...)`
  (`J:unpack/Unpack.java:124`) and PPMd's RangeCoder pulls single bytes via
  `unpackRead.getChar()` (`J:unpack/ppm/RangeCoder.java:75-77`, `Unpack.java:1032`).

RULE: *New (RAR5) decoders must consume bytes exclusively through `ComprDataIO.unpRead` /
`getChar` and emit through `unpWrite` — that is where decryption, volume spanning, packed-size
metering, and CRC accumulation are interposed. Never hand a decoder the underlying channel.
Header parsing goes through `RawDataIo` so header decryption stays transparent. For RAR5,
volume-boundary CRC semantics differ (per-volume packed CRCs are optional; Blake2sp option) —
extend ComprDataIO, don't bypass it.*

---

## 8. Unsigned arithmetic idioms (the canonical junrar way)

| C++ type/op | junrar idiom | Example |
|---|---|---|
| `byte` value | `int` + `& 0xff` on read; cast `(byte)` on write | `State.getSymbol()` `J:unpack/ppm/State.java:42-48` |
| `ushort` | `int` + `& 0xffff` on read; `(short)` on write | `PPMContext.getNumStats()` `PPMContext.java:80-92`; `RarMemBlock.getNU():80-92` |
| `uint` that stays in-range (heap offsets, sizes) | plain `int`, no masking | all SubAllocator pointers |
| `uint` full-range arithmetic (range coder!) | **`long` + `& 0xFFFFFFFFL` after every op** | `RangeCoder.java:37` `uintMask`; `low/code/range` updates `RangeCoder.java:56-97` |
| `uint` division / compare | do it in the masked long domain | `getCurrentCount()` `RangeCoder.java:60-63` |
| `uint` bit-twiddle where only bit patterns matter | `int` + `>>>` for every C++ `>>` on unsigned | VM SHR `RarVM.java:496-505`; `getMean` `PPMContext.java:232-234` uses `>>>` |
| signed `>>` in C++ (`(int)x >> n`) | Java `>>` (junrar's VM_SAR got this wrong — §3.6) | correct model: `C:rarvm.cpp:414` |
| `-x & mask` trick | works unchanged in int/long domain | `ariDecNormalize` `RangeCoder.java:91`; block-size rounding `RawDataIo.java:56` |
| uint wraparound counters (`byte GlueCount`, `EscCount`) | `int` + `& 0xff` in the setter | `ModelPPM.setEscCount` `ModelPPM.java:289-291`; `StateRef.incFreq` `StateRef.java:54-60` |
| bool↔int flag math (`Flags=(Result<Value1)|...`) | `(cond ? 1 : 0) \| ...` — fully parenthesized | correct instances `PPMContext.java:369-372`; broken instances §3.6 |
| `uint32` truncation `UINT32(x)` | `(int)(x & 0xFFFFFFFFL)` or implicit int overflow | `RarVM.java:281-282` |

A tiny `unsigned/` package exists (`J:unsigned/UnsignedByte|Short|Integer|Long.java`) but is
used only by newer header-parsing code, not by the ported hot paths — the mask idioms above
are the house style.

RULE: *Choose per-variable: (a) value semantics fit in signed range → plain int; (b) full
32-bit unsigned arithmetic (range coders, hashes, sizes that can exceed 2^31) → long +
`& 0xFFFFFFFFL` after every mutation; (c) pure bit patterns → int with `>>>`. Never mix (b)
and (c) in one expression without parentheses (§3.6 is the cautionary tale).*

---

## 9. Skew list — divergences between junrar and unrar 3.7.3

**Dropped C++ features (no Java counterpart):**

- Whole compression/encoding half of PPMd (`PPM_CONTEXT::encode*`, `C:model.hpp:68-70`) and of
  the archive layer — junrar is extract-only.
- `C:rijndael.cpp`, `C:sha1.cpp` (JDK primitives instead, §4/§6); crypt.cpp's pre-RAR3
  cipher paths (Decrypt13/15/20) and the KDF result cache.
- Recovery records (`C:recvol.cpp`, `C:rs.cpp`), comments extraction beyond header parse
  (`C:arccmt.cpp`), console/locale/OS layers (`consio`, `isnt`, `beosea`, `os2ea`, ...),
  `C:int64.cpp` (Java long), `C:unicode.cpp` (Java String).
- Block-header CRC verification (unrar `arcread.cpp` rejects corrupt headers; junrar parses
  `headCRC` but does not check it — §5).
- suballoc's `STRICT_ALIGNMENT_REQUIRED` fallback paths (byte[] never needs them).

**Java-side additions with no C++ counterpart:**

- `Pointer`/`StateRef` flyweight machinery and temp-cursor pools (§1.2-1.3).
- `heap[0]`-as-NULL reservation; free-list and temp-memblock slots appended to the heap
  (§1.1/1.4).
- `AnalyzeHeapDump` byte-diff validation harness (§1.5).
- `RandomAccessInputStream` memory-cache seekability shim; `Volume/VolumeManager` API;
  `UnrarCallback` for volume prompting.
- Exception taxonomy (`exception/*`: `CrcErrorException`, `InitDeciphererFailedException`, …)
  replacing unrar's `ErrHandler` global.
- The `unsigned/` wrapper classes (used by newer header code only).

**Renamed / re-shaped concepts:**

- `RAR_MEM_BLK` → `RarMemBlock`, `RAR_NODE` → `RarNode`, `SEE2_CONTEXT` → `SEE2Context`,
  `VM_PreparedCommand/Operand/Program` → `VMPreparedCommand/Operand/Program`,
  `VM_StandardFilters` → enum `VMStandardFilters`, `BLOCK_LZ/BLOCK_PPM` enum → `BlockTypes`
  (`J:unpack/ppm/BlockTypes.java:27`).
- C++ macros → methods: `GET_VALUE/SET_VALUE` → `getValue/setValue`; `SET_IP` → `setIP`;
  `MBPtr` stays `MBPtr` (`SubAllocator.java:98-100`).
- `IReadOnlyAccess` (historic junrar) → `SeekableReadOnlyByteChannel` (§7).
- `unpack.cpp`'s `UnpackRead` indirection → `Unpack.getChar()`/`ComprDataIO`.

**Behavioral divergences (bugs or accepted costs):**

- VM interpreter arithmetic bugs (§3.6: SAR, CMP flags, ADDB/INC/MUL precedence, `inBuf |=`).
- KDF O(n²) recompute + platform-charset password bytes (§4).
- `RawDataIo`/`VMPreparedProgram` boxing (`LinkedList<Byte>`, `Vector<Byte>`) — performance
  wart, replicate with primitive buffers in new code.
- `maxOpCount` is an instance field reset in `ExecuteCode` (`RarVM.java:55`, `223`) vs C++
  local — harmless, but means `setIP` decrements shared state.
- junrar `MaxMB` clamp differs: `decodeInit` caps suballocator size at 1<<20*2 (`MaxMBLimit=1`,
  `ModelPPM.java:186-189`) — a junrar-added DoS guard absent in 3.7.3.

**RAR5-relevant gaps (state explicitly):**

- No Blake2sp (§5). No PBKDF2 wiring yet (§4 — trivial via JDK). No AES-256 path (key size is
  the only change). No RAR5 header format anywhere (`rarfile/*` is RAR ≤4 layout). RarVM is
  legacy-only in RAR5 (filters became fixed native types — junrar's standard-filter native
  path is the model to follow; the bytecode interpreter need not grow).

---

## Meta-patterns (the port's DNA, condensed)

1. **Comment-anchored line porting.** Translated methods keep the original C++ line as an
   inline comment (`// memcpy(ptr,OldPtr,U2B(OldNU));` `SubAllocator.java:275`;
   `// =MinContext->Suffix;` `ModelPPM.java:249`). Preserve this convention — it is the
   traceability layer that makes later upstream diffs mergeable.
2. **"Bug fixed" markers.** Where the porter had to deviate to be correct, the diff is flagged
   (`// Bug fixed`, `SubAllocator.java:150`, `190-201`; `ModelPPM.java:145`,
   `568-575`). Adopt the same explicit marker for any deliberate divergence.
3. **Structure-preserving, not idiomatic.** Control flow, names, even gotos are emulated
   (`noLoop`/`loopEntry` booleans replacing C++ `goto NO_LOOP/LOOP_ENTRY`,
   `ModelPPM.java:397-434`). Refactor *after* byte-level validation, never during the port.
4. **Primitives from the platform, algorithms from the source.** AES/SHA-1/CRC32 were
   eventually all delegated to the JDK (the CRC table port survived 19 years before the 2026
   intrinsic swap); PPMd/VM/KDF math is hand-ported. Apply the same split to RAR5.
