/*
TOD - Trace Oriented Debugger.
Copyright (c) 2006-2008, Guillaume Pothier
All rights reserved.

Redistribution and use in source and binary forms, with or without modification, 
are permitted provided that the following conditions are met:

    * Redistributions of source code must retain the above copyright notice, this 
      list of conditions and the following disclaimer.
    * Redistributions in binary form must reproduce the above copyright notice, 
      this list of conditions and the following disclaimer in the documentation 
      and/or other materials provided with the distribution.
    * Neither the name of the University of Chile nor the names of its contributors 
      may be used to endorse or promote products derived from this software without 
      specific prior written permission.

THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND ANY 
EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED 
WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED. 
IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, 
INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT 
NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR 
PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, 
WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) 
ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE 
POSSIBILITY OF SUCH DAMAGE.

Parts of this work rely on the MD5 algorithm "derived from the RSA Data Security, 
Inc. MD5 Message-Digest Algorithm".
*/
package tod.impl.replay;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;
import java.util.Set;

import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Label;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.FieldNode;
import org.objectweb.asm.tree.IincInsnNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.JumpInsnNode;
import org.objectweb.asm.tree.LabelNode;
import org.objectweb.asm.tree.LdcInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.TryCatchBlockNode;
import org.objectweb.asm.tree.TypeInsnNode;
import org.objectweb.asm.tree.VarInsnNode;

import tod.Util;
import tod.core.config.TODConfig;
import tod.core.database.structure.IBehaviorInfo;
import tod.core.database.structure.IClassInfo;
import tod.core.database.structure.IFieldInfo;
import tod.core.database.structure.IStructureDatabase;
import tod.core.database.structure.ObjectId;
import tod.impl.bci.asm2.BCIUtils;
import tod.impl.bci.asm2.MethodInfo;
import tod.impl.bci.asm2.SyntaxInsnList;
import tod.impl.bci.asm2.MethodInfo.BCIFrame;

public class MethodReplayerGenerator
{
	public static final Type TYPE_OBJECTID = Type.getType(ObjectId.class);
	public static final String CLS_REPLAYER = BCIUtils.getJvmClassName(InScopeReplayerFrame.class);

	private final TODConfig itsConfig;
	private final IStructureDatabase itsDatabase;
	private final ClassNode itsTarget;
	
	private final ClassNode itsClassNode;
	private final MethodNode itsMethodNode;
	
	private final MethodInfo itsMethodInfo;
	
	private Type[] itsArgTypes;
	private Type itsReturnType;
	
	/**
	 * A variable slot that can hold a normal or double value.
	 */
	private int itsTmpVar;
	
	/**
	 * The set of fields that replace local variables
	 */
	private Set<String> itsCreatedFields = new HashSet<String>();
	
	/**
	 * The list of blocks.
	 */
	private List<BlockData> itsBlocks = new ArrayList<BlockData>();
	
	/**
	 * Maps field keys (see {@link #getFieldKey(FieldInsnNode)}) to the corresponding cache slot. 
	 */
	private Map<String, Integer> itsFieldCacheMap = new HashMap<String, Integer>();
	
	/**
	 * A string that represents the mapping from handler ids to block ids.
	 */
	private String itsEncodedHandlerBlocks;
	
	private List<LabelNode> itsHandlers = new ArrayList<LabelNode>();
	
	// Number of local variable slots used for each type 
	private int itsLastRefSlot = 0;
	private int itsLastIntSlot = 0;
	private int itsLastDoubleSlot = 0;
	private int itsLastFloatSlot = 0;
	private int itsLastLongSlot = 0;
	
	public MethodReplayerGenerator(TODConfig aConfig, IStructureDatabase aDatabase, ClassNode aClassNode, MethodNode aMethodNode)
	{
		itsConfig = aConfig;
		itsDatabase = aDatabase;
		itsClassNode = aClassNode;
		itsMethodNode = aMethodNode;
		
		itsArgTypes = Type.getArgumentTypes(itsMethodNode.desc);
		itsReturnType = Type.getReturnType(itsMethodNode.desc);
		
		itsTarget = new ClassNode();
		itsTarget.name = ThreadReplayer.makeReplayerClassName(itsClassNode.name, itsMethodNode.name, itsMethodNode.desc);
		itsTarget.superName = CLS_REPLAYER;
		itsTarget.methods.add(itsMethodNode);
		itsTarget.version = Opcodes.V1_5;
		itsTarget.access = Opcodes.ACC_PUBLIC;
		
		itsMethodInfo = new MethodInfo(itsDatabase, itsClassNode, itsMethodNode);
	}
	
	public TODConfig getConfig()
	{
		return itsConfig;
	}
	
	public IStructureDatabase getDatabase()
	{
		return itsDatabase;
	}
	
	public byte[] generate()
	{
		// Allocate first block as block 0
		Label lStart = new Label();
		LabelNode nStart = new LabelNode(lStart);
		lStart.info = nStart;
		itsMethodNode.instructions.insert(nStart);
		int theFirstBlockId = getBlockId(lStart);
		if (theFirstBlockId != 0) throw new RuntimeException();

		boolean theStatic = BCIUtils.isStatic(itsMethodNode.access);
		
		if (theStatic) itsMethodNode.maxLocals++; // Generated method is not static
		itsTmpVar = nextFreeVar(2);
		
		// Pre-process handlers
		findHandlers();
		
		// Create constructor
		addConstructor();
		
		// Modify method
		processInstructions(itsMethodNode.instructions);

		// Post-process handlers
		hookHandlers(itsMethodNode.instructions);
		itsMethodNode.tryCatchBlocks.clear();

		// Ensure a field is created for each arg (even if it is not used in the body)
		int theSlot = 0;
		if (! theStatic)
		{
			getFieldForVar(theSlot, TYPE_OBJECTID);
			theSlot++;
		}
		
		for(Type theType : itsArgTypes)
		{
			getFieldForVar(theSlot, getTypeOrId(theType.getSort()));
			theSlot += theType.getSize();
		}
		
		// Setup infrastructure
		itsMethodNode.name = "proceed";
		itsMethodNode.desc = "(I)V";
		itsMethodNode.access = Opcodes.ACC_PROTECTED;
		itsMethodNode.exceptions = Collections.EMPTY_LIST;
		itsMethodNode.instructions.insert(genBlockSwitch());
		addSlotSetters();
		addSlotGetters();
		
		itsMethodNode.maxStack += 8;
		
		// Output the modified class
		ClassWriter theWriter = new ClassWriter(ClassWriter.COMPUTE_MAXS);
		itsTarget.accept(theWriter);
		
		byte[] theBytecode = theWriter.toByteArray();

		// Check the methods
		try
		{
			BCIUtils.checkClass(theBytecode);
			for(MethodNode theNode : (List<MethodNode>) itsTarget.methods) BCIUtils.checkMethod(itsTarget, theNode);
		}
		catch(Exception e)
		{
			System.err.println("Class "+itsTarget.name+" failed check.");
			e.printStackTrace();
		}
		
		BCIUtils.writeClass(getConfig().get(TODConfig.CLASS_CACHE_PATH)+"/replayer", itsTarget, theBytecode);
		return theBytecode;
	}
	
	/**
	 * Returns the type corresponding to the given sort.
	 * If the sort corresponds to object or array, returns {@link ObjectId}
	 * @param aSort
	 * @return
	 */
	private Type getTypeOrId(int aSort)
	{
		switch(aSort)
		{
		case Type.OBJECT:
		case Type.ARRAY:
			return TYPE_OBJECTID;
		
		default:
			return BCIUtils.getType(aSort);
		}
	}
	
	private void addConstructor()
	{
		MethodNode theConstructor = new MethodNode();
		theConstructor.name = "<init>";
		theConstructor.desc = "()V";
		theConstructor.exceptions = Collections.EMPTY_LIST;
		theConstructor.access = Opcodes.ACC_PUBLIC;
		theConstructor.maxStack = 6;
		theConstructor.maxLocals = 1;
		theConstructor.tryCatchBlocks = Collections.EMPTY_LIST;
		
		SyntaxInsnList s = new SyntaxInsnList(null);
		s.ALOAD(0);
		s.LDC(itsMethodNode.name);
		s.pushInt(itsMethodNode.access);
		s.LDC(itsMethodNode.desc);
		s.LDC(getEncodedFieldCacheCounts());
		s.LDC(itsEncodedHandlerBlocks);
		s.INVOKESPECIAL(
				CLS_REPLAYER, 
				"<init>", 
				"("+BCIUtils.DSC_STRING+"I"+BCIUtils.DSC_STRING+BCIUtils.DSC_STRING+BCIUtils.DSC_STRING+")V");
		s.RETURN();
		
		theConstructor.instructions = s;
		
		itsTarget.methods.add(theConstructor);
	}
	
	/**
	 * Creates a string that represents the number of slots for each field cache type.
	 * Also sets up the field cache map.
	 */
	private String getEncodedFieldCacheCounts()
	{
		char[] theCounts = new char[11]; // There are 11 sorts
		
		List<IFieldInfo> theCachedFields = itsMethodInfo.getCachedFields();
		
		for (IFieldInfo theField : theCachedFields)
		{
			Type theType = Type.getType(theField.getType().getJvmName());
			
			itsFieldCacheMap.put(getFieldKey(theField), Integer.valueOf(theCounts[theType.getSort()]));
			theCounts[theType.getSort()]++;
		}
		
		return new String(theCounts);
	}
	
	/**
	 * Enumerate handlers and set up {@link #itsEncodedHandlerBlocks}.
	 */
	private void findHandlers()
	{
		StringBuilder theBuilder = new StringBuilder();
		Set<Label> theProcessedLabels = new HashSet<Label>();
		
		// Search for handlers using the try-catch nodes. 
		// Each handler is processed only once, even if referred to by various try-catch nodes
		for(TryCatchBlockNode theNode : (List<TryCatchBlockNode>) itsMethodNode.tryCatchBlocks)
		{
			Label theLabel = theNode.handler.getLabel();
			if (theProcessedLabels.add(theLabel)) 
			{
				// Found new handler
				theBuilder.append((char) getBlockId(theLabel));
				itsHandlers.add(theNode.handler);
			}
		}
		
		itsEncodedHandlerBlocks = theBuilder.toString();
	}
	
	/**
	 * Instrument the handlers previously identified by {@link #findHandlers()}
	 * so that the exception is retrieved
	 * from the current value.
	 */
	private void hookHandlers(InsnList aInsns)
	{
		Type theType = TYPE_OBJECTID;
		
		for(LabelNode theHandler : itsHandlers)
		{
			SyntaxInsnList s = new SyntaxInsnList(null);
			s.ALOAD(0);
			s.INVOKEVIRTUAL(CLS_REPLAYER, valueMethodName(theType), "()"+theType.getDescriptor());
			
			aInsns.insert(theHandler, s);
		}
	}
	
	private int getFieldCacheSlot(FieldInsnNode aNode)
	{
		Integer theSlot = itsFieldCacheMap.get(getFieldKey(aNode));
		return theSlot != null ? theSlot.intValue() : -1;
	}
	
	private String getFieldKey(IFieldInfo aField)
	{
		return aField.getDeclaringType().getName()+"_"+aField.getName();
	}
	
	private String getFieldKey(FieldInsnNode aNode)
	{
		return aNode.owner+"_"+aNode.name;
	}

	/**
	 * Allocates a block id for the given block start label
	 */
	private int getBlockId(Label aLabel)
	{
		int theId = itsBlocks.size(); 
		itsBlocks.add(new BlockData(aLabel, theId));
		return theId;
	}
	
	/**
	 * Generates the switch statement that jumps to a specific block
	 */
	private InsnList genBlockSwitch()
	{
		SyntaxInsnList s = new SyntaxInsnList(null);
		Label theDefault = new Label();
		
		int theCount = itsBlocks.size();
		
		// Sort blocks by id
		Collections.sort(itsBlocks);
		
		// Obtain labels
		Label[] theLabels = new Label[theCount];
		for(int i=0;i<theCount;i++) theLabels[i] = itsBlocks.get(i).label;
		
		// Generate tableswitch
		s.ILOAD(1);
		s.TABLESWITCH(0, theCount-1, theDefault, theLabels);
		
		// Generate default case
		s.label(theDefault);
		BCIUtils.throwRTEx(s, "Bad block id");
		
		return s;
	}
	
	/**
	 * Generates a block of code that saves the operand stack into
	 * the {@link InScopeReplayerFrame}'s stack.
	 * The first aArgCount elements are saved to the arg stack instead of the save stack.
	 */
	private InsnList genSaveStack(BCIFrame aFrame, int aArgCount)
	{
		SyntaxInsnList s = new SyntaxInsnList(null);
		int theStackSize = aFrame.getStackSize();
		
		for(int i=theStackSize-1;i>=0;i--)
		{
			Type theType = aFrame.getStack(i).getType();
			switch(theType.getSize())
			{
			case 1:
				s.ALOAD(0);
				s.SWAP();
				break;
				
			case 2:
				s.ISTORE(theType, itsTmpVar);
				s.ALOAD(0);
				s.ILOAD(theType, itsTmpVar);
				break;
				
			default: throw new RuntimeException("Nooo");
			}
			
			String[] theSig = pushMethodSigBase(theType);
			if (aArgCount-- > 0) theSig[0] = "a"+theSig[0];
			else theSig[0] = "s"+theSig[0];				
			s.INVOKEVIRTUAL(CLS_REPLAYER, theSig[0], theSig[1]);
		}
		
		return s;
	}
	
	private InsnList genSaveStack(BCIFrame aFrame)
	{
		return genSaveStack(aFrame, 0);
	}

	
	/**
	 * Same as {@link #genLoadStack(BCIFrame, int)}, with 0 skip
	 */
	private InsnList genLoadStack(BCIFrame aFrame)
	{
		return genLoadStack(aFrame, 0);
	}
	
	/**
	 * Generates a block of code that loads the operand stack from
	 * the {@link InScopeReplayerFrame}'s stack.
	 * @param aSkip Number of stack slots to skip (counted from the top of the stack).
	 */
	private InsnList genLoadStack(BCIFrame aFrame, int aSkip)
	{
		SyntaxInsnList s = new SyntaxInsnList(null);
		int theStackSize = aFrame.getStackSize();
		for(int i=0;i<theStackSize-aSkip;i++)
		{
			Type theType = aFrame.getStack(i).getType();
			s.ALOAD(0);
			String[] theSig = popMethodSig(theType);
			s.INVOKEVIRTUAL(CLS_REPLAYER, theSig[0], theSig[1]);
		}
		
		return s;
	}
	
	private int nextFreeVar(int aSize)
	{
		int theVar = itsMethodNode.maxLocals;
		itsMethodNode.maxLocals += aSize;
		return theVar;
	}
	
	/**
	 * Returns the name of the field that holds the value for the specified local variable, 
	 * and ensures that the field is created.
	 * There is one field per (slot, type) combination. 
	 */
	private String getFieldForVar(int aSlot, Type aType)
	{
		String theName;
		
		switch(aType.getSort())
		{
        case Type.BOOLEAN:
        case Type.BYTE:
        case Type.CHAR:
        case Type.SHORT:
        case Type.INT:
        	theName = "vInt_"+aSlot;
        	itsLastIntSlot = Math.max(itsLastIntSlot, aSlot);
        	break;
        	
        case Type.FLOAT:
        	theName = "vFloat_"+aSlot;
        	itsLastFloatSlot = Math.max(itsLastFloatSlot, aSlot);
        	break;
        	
        case Type.LONG:
        	theName = "vLong_"+aSlot;
        	itsLastLongSlot = Math.max(itsLastLongSlot, aSlot);
        	break;
        	
        case Type.DOUBLE:
        	theName = "vDouble_"+aSlot;
        	itsLastDoubleSlot = Math.max(itsLastDoubleSlot, aSlot);
        	break;

        case Type.OBJECT:
        case Type.ARRAY:
        	theName = "vRef_"+aSlot;
        	itsLastRefSlot = Math.max(itsLastRefSlot, aSlot);
        	break;
        	
        default:
            throw new RuntimeException("Not handled: "+aType);
		}
		
		if (itsCreatedFields.add(theName))
		{
			itsTarget.fields.add(new FieldNode(Opcodes.ACC_PRIVATE, theName, aType.getDescriptor(), null, null));
		}
		
		return theName;
	}
	
	/**
	 * Adds the implementation of the {@link InScopeReplayerFrame#lRefSet(int, ObjectId)}
	 * methods.
	 */
	private void addSlotSetters()
	{
		addSlotSetter(itsLastRefSlot+1, "Ref", TYPE_OBJECTID);
		addSlotSetter(itsLastIntSlot+1, "Int", Type.INT_TYPE);
		addSlotSetter(itsLastDoubleSlot+1, "Double", Type.DOUBLE_TYPE);
		addSlotSetter(itsLastFloatSlot+1, "Float", Type.FLOAT_TYPE);
		addSlotSetter(itsLastLongSlot+1, "Long", Type.LONG_TYPE);
	}
	
	private void addSlotSetter(int aSlotCount, String aNameBase, Type aType)
	{
		MethodNode theSetter = new MethodNode();
		theSetter.name = "l"+aNameBase+"Set";
		theSetter.desc = "(I"+aType.getDescriptor()+")V";
		theSetter.access = Opcodes.ACC_PROTECTED;
		theSetter.exceptions = Collections.EMPTY_LIST;
		theSetter.maxStack = 3;
		theSetter.maxLocals = 4;
		theSetter.tryCatchBlocks = Collections.EMPTY_LIST;
		
		SyntaxInsnList s = new SyntaxInsnList(null);
		
		if (aSlotCount > 0)
		{
			Label[] l = new Label[aSlotCount];
			Label lDefault = new Label();
			
			for(int i=0;i<aSlotCount;i++) l[i] = new Label();
			
			s.ILOAD(1);
			s.TABLESWITCH(0, aSlotCount-1, lDefault, l);
			
			for (int i=0;i<aSlotCount;i++)
			{
				s.label(l[i]);
				s.ALOAD(0);
				s.ILOAD(aType, 2);
				s.PUTFIELD(itsTarget.name, getFieldForVar(i, aType), aType.getDescriptor());
				s.RETURN();
			}
			
			s.label(lDefault);
		}
		
		BCIUtils.throwRTEx(s, "No such slot");
		
		theSetter.instructions = s;
		itsTarget.methods.add(theSetter);
	}
	
	/**
	 * Adds the implementation of the {@link InScopeReplayerFrame#lRefGet(int)}
	 * methods.
	 */
	private void addSlotGetters()
	{
		addSlotGetter(itsLastRefSlot+1, "Ref", TYPE_OBJECTID);
	}
	
	private void addSlotGetter(int aSlotCount, String aNameBase, Type aType)
	{
		MethodNode theGetter = new MethodNode();
		theGetter.name = "l"+aNameBase+"Get";
		theGetter.desc = "(I)"+aType.getDescriptor();
		theGetter.access = Opcodes.ACC_PROTECTED;
		theGetter.exceptions = Collections.EMPTY_LIST;
		theGetter.maxStack = 3;
		theGetter.maxLocals = 2;
		theGetter.tryCatchBlocks = Collections.EMPTY_LIST;
		
		SyntaxInsnList s = new SyntaxInsnList(null);
		
		if (aSlotCount > 0)
		{
			Label[] l = new Label[aSlotCount];
			Label lDefault = new Label();
			for(int i=0;i<aSlotCount;i++) l[i] = new Label();
			
			s.ILOAD(1);
			s.TABLESWITCH(0, aSlotCount-1, lDefault, l);
			
			for (int i=0;i<aSlotCount;i++)
			{
				s.label(l[i]);
				s.ALOAD(0);
				s.GETFIELD(itsTarget.name, getFieldForVar(i, aType), aType.getDescriptor());
				s.IRETURN(aType);
			}
			
			s.label(lDefault);
		}
		
		BCIUtils.throwRTEx(s, "No such slot");
		
		theGetter.instructions = s;
		itsTarget.methods.add(theGetter);
	}
	
	/**
	 * Returns the name of the {@link InScopeReplayerFrame#vBoolean()} method
	 * corresponding to the given type.
	 */
	private String valueMethodName(Type aType)
	{
		switch(aType.getSort())
		{
		case Type.ARRAY:
		case Type.OBJECT: return "vRef";
		case Type.BOOLEAN: return "vBoolean";
		case Type.BYTE: return "vByte";
		case Type.CHAR: return "vChar";
		case Type.DOUBLE: return "vDouble";
		case Type.FLOAT: return "vFloat";
		case Type.INT: return "vInt";
		case Type.LONG: return "vLong";
		case Type.SHORT: return "vShort";
		default: throw new RuntimeException("Unknown type: "+aType);
		}
	}
	
	/**
	 * Returns the base name and descriptor of the {@link InScopeReplayerFrame#sIntPush(int)}
	 * or {@link InScopeReplayerFrame#aIntPush(int)} push methods
	 * corresponding to the given type.
	 */
	private String[] pushMethodSigBase(Type aType)
	{
		switch(aType.getSort())
		{
		case Type.OBJECT:
		case Type.ARRAY:
			return new String[] { "RefPush", "("+BCIUtils.DSC_OBJECTID+")V" };
			
		case Type.BOOLEAN: 
		case Type.BYTE: 
		case Type.CHAR: 
		case Type.SHORT: 
		case Type.INT: 
			return new String[] { "IntPush", "(I)V" };
		
		case Type.DOUBLE: return new String[] { "DoublePush", "(D)V" };
		case Type.FLOAT: return new String[] { "FloatPush", "(F)V" };
		case Type.LONG: return new String[] { "LongPush", "(J)V" };
		default: throw new RuntimeException("Unknown type: "+aType);
		}
	}
	
	/**
	 * Returns the name and descriptor of the {@link InScopeReplayerFrame#vBoolean()} pop method
	 * corresponding to the given type.
	 */
	private String[] popMethodSig(Type aType)
	{
		switch(aType.getSort())
		{
		case Type.OBJECT: 
		case Type.ARRAY:
			return new String[] { "sRefPop", "()"+BCIUtils.DSC_OBJECTID };
			
		case Type.BOOLEAN: 
		case Type.BYTE: 
		case Type.CHAR: 
		case Type.SHORT: 
		case Type.INT: return new String[] { "sIntPop", "()I" };
		
		case Type.DOUBLE: return new String[] { "sDoublePop", "()D" };
		case Type.FLOAT: return new String[] { "sFloatPop", "()F" };
		case Type.LONG: return new String[] { "sLongPop", "()J" };
		default: throw new RuntimeException("Unknown type: "+aType);
		}
	}
	
	private void processInstructions(InsnList aInsns)
	{
		ListIterator<AbstractInsnNode> theIterator = aInsns.iterator();
		while(theIterator.hasNext()) 
		{
			AbstractInsnNode theNode = theIterator.next();
			int theOpcode = theNode.getOpcode();
			
			switch(theOpcode)
			{
			case Opcodes.IRETURN:
			case Opcodes.LRETURN:
			case Opcodes.FRETURN:
			case Opcodes.DRETURN:
			case Opcodes.ARETURN:
			case Opcodes.RETURN:
				processReturn(aInsns, (InsnNode) theNode);
				break;
				
			case Opcodes.ATHROW:
				processThrow(aInsns, (InsnNode) theNode);
				break;
				
			case Opcodes.INVOKEVIRTUAL:
			case Opcodes.INVOKESPECIAL:
			case Opcodes.INVOKESTATIC:
			case Opcodes.INVOKEINTERFACE:
				processInvoke(aInsns, (MethodInsnNode) theNode);
				break;

			case Opcodes.NEWARRAY:
			case Opcodes.ANEWARRAY:
			case Opcodes.MULTIANEWARRAY:
				processNewArray(aInsns, theNode);
				break;
				
			case Opcodes.NEW:
				processNew(aInsns, (TypeInsnNode) theNode);
				break;
				
			case Opcodes.GETFIELD:
			case Opcodes.GETSTATIC:
				processGetField(aInsns, (FieldInsnNode) theNode);
				break;
				
			case Opcodes.PUTFIELD:
			case Opcodes.PUTSTATIC:
				processPutField(aInsns, (FieldInsnNode) theNode);
				break;
				
			case Opcodes.IALOAD:
			case Opcodes.LALOAD:
			case Opcodes.FALOAD:
			case Opcodes.DALOAD:
			case Opcodes.AALOAD:
			case Opcodes.BALOAD:
			case Opcodes.CALOAD:
			case Opcodes.SALOAD:
				processGetArray(aInsns, (InsnNode) theNode);
				break;
				
			case Opcodes.ARRAYLENGTH:
				processArrayLength(aInsns, (InsnNode) theNode);
				break;
				
			case Opcodes.IASTORE:
			case Opcodes.LASTORE:
			case Opcodes.FASTORE:
			case Opcodes.DASTORE:
			case Opcodes.AASTORE:
			case Opcodes.BASTORE:
			case Opcodes.CASTORE:
			case Opcodes.SASTORE:
				processPutArray(aInsns, (InsnNode) theNode);
				break;
				
			case Opcodes.ILOAD:
			case Opcodes.LLOAD:
			case Opcodes.FLOAD:
			case Opcodes.DLOAD:
			case Opcodes.ALOAD:
				processGetVar(aInsns, (VarInsnNode) theNode);
				break;
				
			case Opcodes.ISTORE:
			case Opcodes.LSTORE:
			case Opcodes.FSTORE:
			case Opcodes.DSTORE:
			case Opcodes.ASTORE:
				processPutVar(aInsns, (VarInsnNode) theNode);
				break;
				
			case Opcodes.IINC:
				processIinc(aInsns, (IincInsnNode) theNode);
				break;
				
			case Opcodes.LDC:
				processLdc(aInsns, (LdcInsnNode) theNode);
				break;
				
			case Opcodes.IF_ACMPEQ:
			case Opcodes.IF_ACMPNE:
				processIfAcmp(aInsns, (JumpInsnNode) theNode);
				break;
				
			case Opcodes.IDIV:
			case Opcodes.LDIV:
			case Opcodes.FDIV:
			case Opcodes.DDIV:
			case Opcodes.IREM:
			case Opcodes.LREM:
			case Opcodes.FREM:
			case Opcodes.DREM:
				processDiv(aInsns, (InsnNode) theNode);
				break;
				
			case Opcodes.MONITORENTER:
			case Opcodes.MONITOREXIT:
				processMonitor(aInsns, (InsnNode) theNode);
				break;
				
			case Opcodes.CHECKCAST:
				processCheckCast(aInsns, (TypeInsnNode) theNode);
				break;
			}
		}
	}

	private void processReturn(InsnList aInsns, InsnNode aNode)
	{
		Type theType = getTypeOrId(BCIUtils.getSort(aNode.getOpcode()));
		SyntaxInsnList s = new SyntaxInsnList(null);
		
		if (theType.getSort() != Type.VOID)
		{
			switch(theType.getSize())
			{
			case 1:
				s.ALOAD(0);
				s.SWAP();
				break;
				
			case 2:
				s.ISTORE(theType, itsTmpVar);
				s.ALOAD(0);
				s.ILOAD(theType, itsTmpVar);
				break;
				
			default: throw new RuntimeException("Nooo");
			}
			s.INVOKEVIRTUAL(CLS_REPLAYER, valueMethodName(theType), "("+theType.getDescriptor()+")V");
		}

		s.ALOAD(0);
		s.INVOKEVIRTUAL(CLS_REPLAYER, "processReturn", "()V");
		s.RETURN();
		
		aInsns.insert(aNode, s);
		aInsns.remove(aNode);
	}
	
	private void processThrow(InsnList aInsns, InsnNode aNode)
	{
		SyntaxInsnList s = new SyntaxInsnList(null);
		
		s.ALOAD(0);
		s.INVOKEVIRTUAL(CLS_REPLAYER, "expectException", "()V");
		s.RETURN();
		
		aInsns.insert(aNode, s);
		aInsns.remove(aNode);
	}
	
	/**
	 * Returns the id of the behavior invoked by the given node.
	 */
	private int getBehaviorId(MethodInsnNode aNode)
	{
		IClassInfo theClass = getDatabase().getClass(Util.jvmToScreen(aNode.owner), true);
		IBehaviorInfo theBehavior = theClass.getBehavior(aNode.name, aNode.desc);
		return theBehavior.getId();
	}
	

	
	private void processInvoke(InsnList aInsns, MethodInsnNode aNode)
	{
		Type[] theArgTypes = Type.getArgumentTypes(aNode.desc);
		Type theType = getTypeOrId(Type.getReturnType(aNode.desc).getSort());
		int theBehaviorId = getBehaviorId(aNode);
		boolean theStatic = aNode.getOpcode() == Opcodes.INVOKESTATIC;
		int theArgCount = theArgTypes.length;
		if (! theStatic) theArgCount++;
		BCIFrame theFrame = itsMethodInfo.getFrame(aNode);

		boolean theExpectObjectInitialized = 
			"<init>".equals(aNode.name) 
			&& ! itsMethodInfo.isChainingInvocation(aNode)
			&& ! getDatabase().isInScope(aNode.owner);
		
		SyntaxInsnList s = new SyntaxInsnList(null);
		
		{
			// Generate block id
			Label l = new Label();
			int theBlockId = getBlockId(l);
			
			// Save state
			s.add(genSaveStack(theFrame, theArgCount)); // The stack will be popped by the next replayer
			s.ALOAD(0);
			s.pushInt(theBehaviorId);
			s.pushInt(theBlockId);
			s.pushInt(theExpectObjectInitialized ? 1 : 0);
			s.INVOKEVIRTUAL(CLS_REPLAYER, "invoke", "(IIZ)V");
			s.RETURN();
			
			// Got result
			s.label(l);
			s.add(genLoadStack(theFrame, theArgCount));
			
			if (theType.getSort() != Type.VOID)
			{
				s.ALOAD(0);
				s.INVOKEVIRTUAL(CLS_REPLAYER, valueMethodName(theType), "()"+theType.getDescriptor());
			}
		}
		
		// Wait for constructor target event if applicable
		if (itsMethodInfo.isChainingInvocation(aNode))
		{
			Label l = new Label();
			int theBlockId = getBlockId(l);
			BCIFrame theFrame2 = itsMethodInfo.getFrame(aNode.getNext());

			// Save state
			s.add(genSaveStack(theFrame2)); 
			s.ALOAD(0);
			s.pushInt(theBlockId);
			s.INVOKEVIRTUAL(CLS_REPLAYER, "expectConstructorTarget", "(I)V");
			s.RETURN();
			
			// Got event
			s.label(l);
			s.add(genLoadStack(theFrame2));
		}

		aInsns.insert(aNode, s);
		aInsns.remove(aNode);
	}

	private void processNew(InsnList aInsns, TypeInsnNode aNode)
	{
		SyntaxInsnList s = new SyntaxInsnList(null);
		
		s.ALOAD(0);
		s.INVOKEVIRTUAL(CLS_REPLAYER, "nextTmpId", "()"+BCIUtils.DSC_OBJECTID);
		
		aInsns.insert(aNode, s);
		aInsns.remove(aNode);
	}

	private void processNewArray(InsnList aInsns, AbstractInsnNode aNode)
	{
		Type theType = TYPE_OBJECTID;
		BCIFrame theFrame = itsMethodInfo.getFrame(aNode);

		SyntaxInsnList s = new SyntaxInsnList(null);
		
		// Generate block id
		Label l = new Label();
		int theBlockId = getBlockId(l);
		
		// Save state
		s.add(genSaveStack(theFrame));
		s.ALOAD(0);
		s.pushInt(theBlockId);
		s.INVOKEVIRTUAL(CLS_REPLAYER, "expectNewArray", "(I)V");
		s.RETURN();
		
		// Got value
		s.label(l);
		s.add(genLoadStack(theFrame, 1));
		s.ALOAD(0);
		s.INVOKEVIRTUAL(CLS_REPLAYER, valueMethodName(theType), "()"+theType.getDescriptor());
		
		aInsns.insert(aNode, s);
		aInsns.remove(aNode);
	}
	
	/**
	 * LDC of class constant can throw an exception
	 */
	private void processLdc(InsnList aInsns, LdcInsnNode aNode)
	{
		if (! (aNode.cst instanceof Type) && ! (aNode.cst instanceof String)) return;
		
		Type theType = TYPE_OBJECTID;
		BCIFrame theFrame = itsMethodInfo.getFrame(aNode);

		SyntaxInsnList s = new SyntaxInsnList(null);
		
		// Generate block id
		Label l = new Label();
		int theBlockId = getBlockId(l);
		
		// Save state
		s.add(genSaveStack(theFrame));
		s.ALOAD(0);
		s.pushInt(theBlockId);
		s.INVOKEVIRTUAL(CLS_REPLAYER, "expectCst", "(I)V");
		s.RETURN();
		
		// Got value
		s.label(l);
		s.add(genLoadStack(theFrame));
		s.ALOAD(0);
		s.INVOKEVIRTUAL(CLS_REPLAYER, valueMethodName(theType), "()"+theType.getDescriptor());
		
		aInsns.insert(aNode, s);
		aInsns.remove(aNode);
	}
	
	/**
	 * References are transformed into {@link ObjectId} so we must compare ids.
	 */
	private void processIfAcmp(InsnList aInsns, JumpInsnNode aNode)
	{
		SyntaxInsnList s = new SyntaxInsnList(null);

		s.INVOKESTATIC(CLS_REPLAYER, "cmpId", "("+BCIUtils.DSC_OBJECTID+BCIUtils.DSC_OBJECTID+")Z");
		switch(aNode.getOpcode())
		{
		case Opcodes.IF_ACMPEQ: s.IFtrue(aNode.label.getLabel()); break;
		case Opcodes.IF_ACMPNE: s.IFfalse(aNode.label.getLabel()); break;
		default:
			throw new RuntimeException("Not handled: "+aNode);
		}
		
		aInsns.insert(aNode, s);
		aInsns.remove(aNode);
	}

	private void processGetField(InsnList aInsns, FieldInsnNode aNode)
	{
		Type theType = getTypeOrId(Type.getType(aNode.desc).getSort());
		BCIFrame theFrame = itsMethodInfo.getFrame(aNode);

		SyntaxInsnList s = new SyntaxInsnList(null);
		
		// Generate block id
		Label l = new Label();
		int theBlockId = getBlockId(l);
		
		// Save state
		s.add(genSaveStack(theFrame));
		s.ALOAD(0);
		s.pushInt(theType.getSort());
		s.pushInt(theBlockId);
		s.pushInt(getFieldCacheSlot(aNode));
		s.INVOKEVIRTUAL(CLS_REPLAYER, "expectField", "(III)V");
		s.RETURN();
		
		// Got value
		s.label(l);
		s.add(genLoadStack(theFrame, 1));
		s.ALOAD(0);
		s.INVOKEVIRTUAL(CLS_REPLAYER, valueMethodName(theType), "()"+theType.getDescriptor());
		
		aInsns.insert(aNode, s);
		aInsns.remove(aNode);
	}
	
	private void processPutField(InsnList aInsns, FieldInsnNode aNode)
	{
		Type theType = getTypeOrId(Type.getType(aNode.desc).getSort());
		
		SyntaxInsnList s = new SyntaxInsnList(null);
		s.POP(theType); // Pop value
		if (aNode.getOpcode() != Opcodes.PUTSTATIC) s.POP(); // Pop target
		
		aInsns.insert(aNode, s);
		aInsns.remove(aNode);
	}
	
	private void processPutArray(InsnList aInsns, InsnNode aNode)
	{
		Type theElementType = getTypeOrId(BCIUtils.getSort(aNode.getOpcode()));
		
		SyntaxInsnList s = new SyntaxInsnList(null);
		s.POP(theElementType); // Pop value
		s.POP(); // Pop index
		s.POP(); // Pop array
		
		aInsns.insert(aNode, s);
		aInsns.remove(aNode);
	}
	


	private void processGetArray(InsnList aInsns, InsnNode aNode)
	{
		Type theType = getTypeOrId(BCIUtils.getSort(aNode.getOpcode()));
		BCIFrame theFrame = itsMethodInfo.getFrame(aNode);
		
		SyntaxInsnList s = new SyntaxInsnList(null);
		
		// Generate block id
		Label l = new Label();
		int theBlockId = getBlockId(l);
		
		s.add(genSaveStack(theFrame));
		s.ALOAD(0);
		s.pushInt(theType.getSort());
		s.pushInt(theBlockId);
		s.INVOKEVIRTUAL(CLS_REPLAYER, "expectArray", "(II)V");
		s.RETURN();
		
		// Got value
		s.label(l);
		s.add(genLoadStack(theFrame, 2));
		s.ALOAD(0);
		s.INVOKEVIRTUAL(CLS_REPLAYER, valueMethodName(theType), "()"+theType.getDescriptor());
		
		aInsns.insert(aNode, s);
		aInsns.remove(aNode);
	}

	private void processArrayLength(InsnList aInsns, InsnNode aNode)
	{
		Type theType = Type.INT_TYPE;
		BCIFrame theFrame = itsMethodInfo.getFrame(aNode);
		
		SyntaxInsnList s = new SyntaxInsnList(null);
		
		// Generate block id
		Label l = new Label();
		int theBlockId = getBlockId(l);
		
		s.add(genSaveStack(theFrame));
		s.ALOAD(0);
		s.pushInt(theBlockId);
		s.INVOKEVIRTUAL(CLS_REPLAYER, "expectArrayLength", "(I)V");
		s.RETURN();
		
		// Got value
		s.label(l);
		s.add(genLoadStack(theFrame, 1));
		s.ALOAD(0);
		s.INVOKEVIRTUAL(CLS_REPLAYER, valueMethodName(theType), "()"+theType.getDescriptor());
		
		aInsns.insert(aNode, s);
		aInsns.remove(aNode);
	}
	
	private void processGetVar(InsnList aInsns, VarInsnNode aNode)
	{
		Type theType = getTypeOrId(BCIUtils.getSort(aNode.getOpcode()));
		String theField = getFieldForVar(aNode.var, theType);
		
		SyntaxInsnList s = new SyntaxInsnList(null);
		s.ALOAD(0);
		s.GETFIELD(itsTarget.name, theField, theType.getDescriptor());
		
		aInsns.insert(aNode, s);
		aInsns.remove(aNode);
	}

	private void processPutVar(InsnList aInsns, VarInsnNode aNode)
	{
		Type theType = getTypeOrId(BCIUtils.getSort(aNode.getOpcode()));
		String theField = getFieldForVar(aNode.var, theType);
		
		SyntaxInsnList s = new SyntaxInsnList(null);
		switch(theType.getSize())
		{
		case 1:
			s.ALOAD(0);
			s.SWAP();
			break;
			
		case 2:
			s.ISTORE(theType, itsTmpVar);
			s.ALOAD(0);
			s.ILOAD(theType, itsTmpVar);
			break;
			
		default: throw new RuntimeException("Nooo");
		}

		s.PUTFIELD(itsTarget.name, theField, theType.getDescriptor());
		
		aInsns.insert(aNode, s);
		aInsns.remove(aNode);
	}
	
	private void processIinc(InsnList aInsns, IincInsnNode aNode)
	{
		Type theType = Type.INT_TYPE;
		String theField = getFieldForVar(aNode.var, theType);
		
		SyntaxInsnList s = new SyntaxInsnList(null);
		
		s.ALOAD(0); // this
		s.DUP(); // this, this
		s.GETFIELD(itsTarget.name, theField, theType.getDescriptor()); // this, var
		s.pushInt(aNode.incr); // this, var, incr
		s.IADD(); // this, newvar
		s.PUTFIELD(itsTarget.name, theField, theType.getDescriptor());
		
		aInsns.insert(aNode, s);
		aInsns.remove(aNode);
	}
	
	/**
	 * We need to check if an exception is going to be thrown.
	 */
	private void processDiv(InsnList aInsns, InsnNode aNode)
	{
		Type theType = BCIUtils.getType(BCIUtils.getSort(aNode.getOpcode()));

		SyntaxInsnList s = new SyntaxInsnList(null);
		Label lNormal = new Label();
		s.DUP(theType);
		switch(theType.getSort()) 
		{
		case Type.BOOLEAN:
		case Type.BYTE:
		case Type.CHAR:
		case Type.SHORT:
		case Type.INT: break;
		
		case Type.LONG:
			s.pushLong(0);
			s.LCMP();
			break;
		
		case Type.DOUBLE:
			s.pushDouble(0.0);
			s.DCMPG();
			break;
			
		case Type.FLOAT:
			s.pushFloat(0f);
			s.FCMPG();
			break;
		
		default: throw new RuntimeException("Unexpected type: "+theType);

		}
		
		s.IFNE(lNormal);
		
		{
			// An arithmetic exception will occur
			s.POP(theType);
			s.POP(theType);
			
			s.ALOAD(0);
			s.INVOKEVIRTUAL(CLS_REPLAYER, "expectException", "()V");
			s.RETURN();
		}
		
		s.label(lNormal);
		aInsns.insertBefore(aNode, s);
	}
	
	/**
	 * Put execution on hold until next message is received
	 */
	private void processCheckCast(InsnList aInsns, TypeInsnNode aNode)
	{
		BCIFrame theFrame = itsMethodInfo.getFrame(aNode);
		
		SyntaxInsnList s = new SyntaxInsnList(null);

		// Generate block id
		Label l = new Label();
		int theBlockId = getBlockId(l);
		
		s.add(genSaveStack(theFrame));
		s.ALOAD(0);
		s.pushInt(theBlockId);
		s.INVOKEVIRTUAL(CLS_REPLAYER, "checkCast", "(I)V");
		s.RETURN();
		
		// Got value
		s.label(l);
		s.add(genLoadStack(theFrame));
		
		aInsns.insert(aNode, s);
		aInsns.remove(aNode);
	}

	
	private void processMonitor(InsnList aInsns, InsnNode aNode)
	{
		SyntaxInsnList s = new SyntaxInsnList(null);
		s.POP();
		
		aInsns.insert(aNode, s);
		aInsns.remove(aNode);
	}
	
	private static final class BlockData implements Comparable<BlockData>
	{
		public final Label label;
		public final int id;
		
		public BlockData(Label aLabel, int aId)
		{
			label = aLabel;
			id = aId;
		}

		public int compareTo(BlockData o)
		{
			return id-o.id;
		}
	}
}
