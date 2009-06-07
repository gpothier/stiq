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
package tod.impl.bci.asm2;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;
import java.util.Set;

import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.JumpInsnNode;
import org.objectweb.asm.tree.LookupSwitchInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.TableSwitchInsnNode;
import org.objectweb.asm.tree.VarInsnNode;
import org.objectweb.asm.tree.analysis.Analyzer;
import org.objectweb.asm.tree.analysis.AnalyzerException;
import org.objectweb.asm.tree.analysis.BasicInterpreter;
import org.objectweb.asm.tree.analysis.BasicValue;
import org.objectweb.asm.tree.analysis.Frame;
import org.objectweb.asm.tree.analysis.Interpreter;
import org.objectweb.asm.tree.analysis.SmallSet;
import org.objectweb.asm.tree.analysis.SourceInterpreter;
import org.objectweb.asm.tree.analysis.SourceValue;
import org.objectweb.asm.tree.analysis.Value;

import tod.core.database.structure.IFieldInfo;
import tod.core.database.structure.IMutableStructureDatabase;
import zz.utils.ArrayStack;
import zz.utils.ListMap;
import zz.utils.Stack;
import zz.utils.Utils;

/**
 * Aggregates analysis information about a method that is needed by both the
 * instrumenter and the replayer.
 * @author gpothier
 */
public class MethodInfo
{
	private final IMutableStructureDatabase itsDatabase;
	private final ClassNode itsClassNode;
	private final MethodNode itsMethodNode;
	
	/**
	 * Maps instructions to frames.
	 */
	private Map<AbstractInsnNode, BCIFrame> itsFramesMap;
	
	/**
	 * Maps each field to the instructions that read that field on self.
	 * Note: the same instruction can appear twice, which is actually what ultimately
	 * triggers the field to be cached.
	 */
	private ListMap<IFieldInfo, FieldInsnNode> itsAccessMap = new ListMap<IFieldInfo, FieldInsnNode>();

	/**
	 * Maps field access instructions to the local variable slot that holds the cached value.
	 */
	private Map<FieldInsnNode, Integer> itsCachedFieldAccesses = new HashMap<FieldInsnNode, Integer>();
	
	/**
	 * Instructions that initializes each slot of the field cache.
	 */
	private InsnList itsFieldCacheInit;
	
	/**
	 * For constructors, the invocation instructions that corresponds to constructor chaining.
	 */
	private MethodInsnNode itsChainingInvocation;

	public MethodInfo(IMutableStructureDatabase aDatabase, ClassNode aClassNode, MethodNode aMethodNode)
	{
		itsDatabase = aDatabase;
		itsClassNode = aClassNode;
		itsMethodNode = aMethodNode;
		setupFrames();
		mapSelfAccesses();
		setupChainingInvocation();
	}

	public ClassNode getClassNode()
	{
		return itsClassNode;
	}
	
	public MethodNode getMethodNode()
	{
		return itsMethodNode;
	}
	
	public IMutableStructureDatabase getDatabase()
	{
		return itsDatabase;
	}
	
	/**
	 * Gets the frame corresponding to the specified node.
	 * @param aNode A node that is part of the original method body.
	 */
	public BCIFrame getFrame(AbstractInsnNode aNode)
	{
		return itsFramesMap.get(aNode);
	}
	
	/**
	 * Determines if the given node is an invocation that corresponds to constructor chaining.
	 */
	public boolean isChainingInvocation(MethodInsnNode aNode)
	{
		return aNode == itsChainingInvocation;
	}
	
	/**
	 * Determines if the given node corresponds to the initial constructor call
	 * (vs. constructor chaining).
	 */
	public boolean isObjectInitialization(MethodInsnNode aNode)
	{
		if (! BCIUtils.isConstructorCall(aNode)) return false;
		else return !isChainingInvocation(aNode);
	}
	

	/**
	 * Allocates local variable slots for field caches.
	 * @param aFirstFreeSlot The first free local slot
	 * @return The number of slots used for caches
	 */
	public int setupLocalCacheSlots(int aFirstFreeSlot)
	{
		int theSlotCount = 0;
		
		// Set up the final structure
		SyntaxInsnList s = new SyntaxInsnList(null);
		Iterator<Map.Entry<IFieldInfo, List<FieldInsnNode>>> theIterator = itsAccessMap.entrySet().iterator();
		while(theIterator.hasNext())
		{
			Map.Entry<IFieldInfo, List<FieldInsnNode>> theEntry = theIterator.next();
			if (theEntry.getValue().size() >= 2)
			{
				String theDesc = theEntry.getValue().get(0).desc;
				Type theType = Type.getType(theDesc);
				int theSlot = aFirstFreeSlot+theSlotCount;
				theSlotCount += theType.getSize();
				
				// Register instruction in the map
				for(FieldInsnNode theNode : theEntry.getValue()) 
					itsCachedFieldAccesses.put(theNode, theSlot);
				
				// Create initializing instruction.
				s.pushDefaultValue(theType);
				s.ISTORE(theType, theSlot);
			}
		}
		
		itsFieldCacheInit = s;
		
		return theSlotCount;
	}

	/**
	 * Returns a list of all cached fields.
	 */
	public List<IFieldInfo> getCachedFields()
	{
		List<IFieldInfo> theResult = new ArrayList<IFieldInfo>();
		
		Iterator<Map.Entry<IFieldInfo, List<FieldInsnNode>>> theIterator = itsAccessMap.entrySet().iterator();
		while(theIterator.hasNext())
		{
			Map.Entry<IFieldInfo, List<FieldInsnNode>> theEntry = theIterator.next();
			if (theEntry.getValue().size() >= 2) theResult.add(theEntry.getKey());
		}
		
		return theResult;
	}
	

	/**
	 * Returns a block of code that initializes the cache for each field.
	 * {@link #setupLocalCacheSlots(int)} must have been called before
	 */
	public InsnList getFieldCacheInitInstructions()
	{
		return itsFieldCacheInit;
	}
	
	/**
	 * Returns the local variable slot to use as a cache for the field accessed
	 * by the given instruction
	 * {@link #setupLocalCacheSlots(int)} must have been called before
	 */
	public Integer getCacheSlot(FieldInsnNode aNode)
	{
		return itsCachedFieldAccesses.get(aNode);
	}
	
	
	private void setupFrames()
	{
		itsFramesMap = new HashMap<AbstractInsnNode, BCIFrame>();
		BCIFrame[] theFrames = analyze_nocflow(getClassNode().name, getMethodNode());
		
		int i = 0; // Instruction rank
		ListIterator<AbstractInsnNode> theIterator = getMethodNode().instructions.iterator();
		while(theIterator.hasNext()) itsFramesMap.put(theIterator.next(), theFrames[i++]);
	}
	
	public static Node[] analyze_cflow(String aClassName, MethodNode aNode)
	{
		SourceInterpreter theInterpreter = new SourceInterpreter();
		Analyzer theAnalyzer = new Analyzer(theInterpreter)
		{
			@Override
			protected Frame newFrame(int nLocals, int nStack)
			{
				return new Node(nLocals, nStack);
			}

			@Override
			protected Frame newFrame(Frame src)
			{
				return new Node(src);
			}

			@Override
			protected void newControlFlowEdge(int aInsn, int aSuccessor)
			{
				Node thePred = (Node) getFrames()[aInsn];
				Node theSucc = (Node) getFrames()[aSuccessor];
				thePred.addSuccessor(theSucc);
				theSucc.addPredecessor(thePred);
			}
		};
		
		try
		{
			theAnalyzer.analyze(aClassName, aNode);
		}
		catch (AnalyzerException e)
		{
			throw new RuntimeException(e);
		}
		
		return (Node[]) theAnalyzer.getFrames();
	}
	
	public static BCIFrame[] analyze_nocflow(String aClassName, MethodNode aNode)
	{
		BCIInterpreter theInterpreter = new BCIInterpreter();
		Analyzer theAnalyzer = new Analyzer(theInterpreter)
		{
			@Override
			protected Frame newFrame(int nLocals, int nStack)
			{
				return new BCIFrame(nLocals, nStack);
			}

			@Override
			protected Frame newFrame(Frame src)
			{
				return new BCIFrame(src);
			}
		};
		
		try
		{
			theAnalyzer.analyze(aClassName, aNode);
		}
		catch (AnalyzerException e)
		{
			throw new RuntimeException(e);
		}
		
		Frame[] theFrames = theAnalyzer.getFrames();
		BCIFrame[] theResult = new BCIFrame[theFrames.length];
		for(int i=0;i<theFrames.length;i++) theResult[i] = (BCIFrame) theFrames[i];
		return theResult;
	}
	
	/**
	 * Whether the given field access instructions accesses a field on self (this).
	 */
	private boolean isSelfFieldAccess(FieldInsnNode aNode)
	{
		if (BCIUtils.isStatic(getMethodNode().access)) return false; //Not an error: a static method can get fields of some object.
		
		BCIValue theTarget = getFrame(aNode).getStack(0);

		for(AbstractInsnNode theNode : theTarget.insns)
		{
			if (theNode instanceof VarInsnNode)
			{
				VarInsnNode theVarInsnNode = (VarInsnNode) theNode;
				if (theVarInsnNode.var == 0) return true;
			}
		}
		
		return false;
	}
	


	/**
	 * For each field access on the current object or static field access,
	 * check if the access might execute more than once (because of several
	 * access locations or of loops). If so, reserve a local variable to hold the
	 * last observed value of the field so that we can optimize the Get Field event.  
	 */
	private void mapSelfAccesses()
	{
		Set<AbstractInsnNode> theVisitedJumps = new HashSet<AbstractInsnNode>();
		

		// A list of paths to process (denoted by the first instruction of the path) 
		Stack<AbstractInsnNode> theWorkList = new ArrayStack<AbstractInsnNode>();
		theWorkList.push(getMethodNode().instructions.getFirst());
		
		// Build the access maps
		while(! theWorkList.isEmpty()) 
		{
			AbstractInsnNode theNode = theWorkList.pop();
			
			// If this flag is true the next instruction is pushed onto
			// the working list at the end of the iteration
			boolean theContinue = false;
			
			if (theNode instanceof FieldInsnNode)
			{
				FieldInsnNode theFieldInsnNode = (FieldInsnNode) theNode;
				
				if (theNode.getOpcode() == Opcodes.GETFIELD)
				{
					if (isSelfFieldAccess(theFieldInsnNode))
					{
						IFieldInfo theField = BCIUtils.getField(getDatabase(), theFieldInsnNode);
						itsAccessMap.add(theField, theFieldInsnNode);
					}
				}
				else if (theNode.getOpcode() == Opcodes.GETSTATIC)
				{
					IFieldInfo theField = BCIUtils.getField(getDatabase(), theFieldInsnNode);
					itsAccessMap.add(theField, theFieldInsnNode);
				}
				
				theContinue = true;
			}
			else if (theNode instanceof JumpInsnNode)
			{
				JumpInsnNode theJumpInsnNode = (JumpInsnNode) theNode;
				if (theVisitedJumps.add(theNode)) 
				{
					theWorkList.push(theJumpInsnNode.label);
					if (theNode.getOpcode() != Opcodes.GOTO) theContinue = true;
				}
			}
			else if (theNode instanceof TableSwitchInsnNode)
			{
				TableSwitchInsnNode theTableSwitchInsnNode = (TableSwitchInsnNode) theNode;
				if (theVisitedJumps.add(theNode))
				{
					theWorkList.push(theTableSwitchInsnNode.dflt);
					theWorkList.pushAll(theTableSwitchInsnNode.labels);
				}
			}
			else if (theNode instanceof LookupSwitchInsnNode)
			{
				LookupSwitchInsnNode theLookupSwitchInsnNode = (LookupSwitchInsnNode) theNode;
				if (theVisitedJumps.add(theNode))
				{
					theWorkList.push(theLookupSwitchInsnNode.dflt);
					theWorkList.pushAll(theLookupSwitchInsnNode.labels);
				}
			}
			else if ((theNode.getOpcode() >= Opcodes.IRETURN && theNode.getOpcode() <= Opcodes.RETURN)
				|| theNode.getOpcode() == Opcodes.RET)
			{
				// Don't continue
			}
			else
			{
				theContinue = true;
			}
			
			if (theContinue && theNode.getNext() != null) theWorkList.push(theNode.getNext()); 
		}

	}
	
	private boolean isConstructor()
	{
		return "<init>".equals(getMethodNode().name);
	}

	private static boolean isALOAD0(AbstractInsnNode aNode)
	{
		if (aNode.getOpcode() == Opcodes.ALOAD)
		{
			VarInsnNode theVarNode = (VarInsnNode) aNode;
			if (theVarNode.var == 0) return true;
		}
		return false;
	}
	
	private boolean hasAload0Only(BCIValue aValue)
	{
		if (aValue.insns.size() != 1) return false;
		return isALOAD0(aValue.insns.iterator().next()); 
	}
	

	/**
	 * For constructors, looks for the invoke instruction that corresponds to constructor
	 * chaining, if any (the only case there is none is for java.lang.Object);
	 */
	private void setupChainingInvocation()
	{
		if (! isConstructor()) return;

		ListIterator<AbstractInsnNode> theIterator = getMethodNode().instructions.iterator();
		while(theIterator.hasNext()) 
		{
			AbstractInsnNode theNode = theIterator.next();
			
			if (BCIUtils.isConstructorCall(theNode))
			{
				BCIFrame theFrame = getFrame(theNode);
				int theArgCount = Type.getArgumentTypes(((MethodInsnNode) theNode).desc).length;
				
				// Check if the target of the call is "this"
				BCIValue theThis = theFrame.getStack(theFrame.getStackSize()-theArgCount-1);
				if (hasAload0Only(theThis)) itsChainingInvocation = (MethodInsnNode) theNode;
			}
		}
		
		if (! BCIUtils.CLS_OBJECT.equals(getClassNode().name) && isConstructor() && itsChainingInvocation == null) 
			throwRTEx("Should have constructor chaining");

	}
	
	private void throwRTEx(String aMessage)
	{
		Utils.rtex(
				"Error in %s.%s%s: %s", 
				getClassNode().name, 
				getMethodNode().name, 
				getMethodNode().desc, 
				aMessage);	
	}

	/**
	 * Blends {@link SourceInterpreter} and {@link BasicInterpreter}
	 * @author gpothier
	 */
	public static class BCIInterpreter implements Interpreter
	{
		private final BasicInterpreter itsBasicInterpreter = new BasicInterpreter();
		private final SourceInterpreter itsSourceInterpreter = new SourceInterpreter();
		
		public Value binaryOperation(AbstractInsnNode aInsn, Value aValue1, Value aValue2)
				throws AnalyzerException
		{
			BasicValue b = (BasicValue) itsBasicInterpreter.binaryOperation(aInsn, aValue1, aValue2);
			SourceValue s = (SourceValue) itsSourceInterpreter.binaryOperation(aInsn, aValue1, aValue2);
			return new BCIValue(b.getType(), s.insns);
		}
		
		public Value copyOperation(AbstractInsnNode aInsn, Value aValue) throws AnalyzerException
		{
			BasicValue b = (BasicValue) itsBasicInterpreter.copyOperation(aInsn, aValue);
			SourceValue s = (SourceValue) itsSourceInterpreter.copyOperation(aInsn, aValue);
			return new BCIValue(b.getType(), s.insns);
		}
		
		public Value merge(Value aV, Value aW)
		{
			BasicValue b = (BasicValue) itsBasicInterpreter.merge(aV, aW);
			SourceValue s = (SourceValue) itsSourceInterpreter.merge(aV, aW);
			return new BCIValue(b.getType(), s.insns);
		}
		
		public Value naryOperation(AbstractInsnNode aInsn, List aValues) throws AnalyzerException
		{
			BasicValue b = (BasicValue) itsBasicInterpreter.naryOperation(aInsn, aValues);
			SourceValue s = (SourceValue) itsSourceInterpreter.naryOperation(aInsn, aValues);
			return new BCIValue(b.getType(), s.insns);
		}
		
		public Value newOperation(AbstractInsnNode aInsn) throws AnalyzerException
		{
			BasicValue b = (BasicValue) itsBasicInterpreter.newOperation(aInsn);
			SourceValue s = (SourceValue) itsSourceInterpreter.newOperation(aInsn);
			return new BCIValue(b.getType(), s.insns);
		}
		
		public Value newValue(Type aType)
		{
			BasicValue b = (BasicValue) itsBasicInterpreter.newValue(aType);
			SourceValue s = (SourceValue) itsSourceInterpreter.newValue(aType);
			return new BCIValue(b.getType(), s.insns);
		}
		
		public Value ternaryOperation(
				AbstractInsnNode aInsn,
				Value aValue1,
				Value aValue2,
				Value aValue3) throws AnalyzerException
		{
			BasicValue b = (BasicValue) itsBasicInterpreter.ternaryOperation(aInsn, aValue1, aValue2, aValue3);
			SourceValue s = (SourceValue) itsSourceInterpreter.ternaryOperation(aInsn, aValue1, aValue2, aValue3);
			return new BCIValue(b.getType(), s.insns);
		}
		
		public Value unaryOperation(AbstractInsnNode aInsn, Value aValue) throws AnalyzerException
		{
			BasicValue b = (BasicValue) itsBasicInterpreter.unaryOperation(aInsn, aValue);
			SourceValue s = (SourceValue) itsSourceInterpreter.unaryOperation(aInsn, aValue);
			return new BCIValue(b.getType(), s.insns);
		}

		public void returnOperation(AbstractInsnNode aInsn, Value aValue, Value aExpected)
		{
		}
	}
	
	/**
	 * A {@link Frame} that contains {@link SourceValue}s.
	 * @author gpothier
	 */
	public static class BCIFrame extends Frame
	{
		public BCIFrame(Frame aSrc)
		{
			super(aSrc);
		}

		public BCIFrame(int aLocals, int aStack)
		{
			super(aLocals, aStack);
		}

		@Override
		public BCIValue getLocal(int aI) throws IndexOutOfBoundsException
		{
			return (BCIValue) super.getLocal(aI);
		}

		@Override
		public BCIValue getStack(int aI) throws IndexOutOfBoundsException
		{
			return (BCIValue) super.getStack(aI);
		}
	}
	
	/**
	 * Combines the information of {@link SourceValue} and {@link BasicValue}
	 * @author gpothier
	 */
	public static class BCIValue implements Value
	{
	    public final Type type;
	    public final Set<AbstractInsnNode> insns;

	    public BCIValue(final Type type) {
	        this(type, SmallSet.EMPTY_SET);
	    }

	    public BCIValue(final Type type, final AbstractInsnNode insn) {
	        this.type = type;
	        this.insns = new SmallSet(insn, null);
	    }

	    public BCIValue(final Type type, final Set<AbstractInsnNode> insns) {
	        this.type = type;
	        this.insns = insns;
	    }

	    public int getSize() {
	        return type.getSize();
	    }

	    @Override
		public boolean equals(final Object value) {
	        if (!(value instanceof BCIValue)) {
	        	return false;
	        }
	        BCIValue v = (BCIValue) value;
	        return type.equals(v.type) && insns.equals(v.insns);
	    }

	    @Override
		public int hashCode() {
	        return insns.hashCode()*27+type.hashCode();
	    }

	}
	
	public static class Node extends Frame 
	{
		private Set<Node> itsSuccessors = new HashSet<Node>();
		private Set<Node> itsPredecessors = new HashSet<Node>();

		public Node(int nLocals, int nStack)
		{
			super(nLocals, nStack);
		}

		public Node(Frame src)
		{
			super(src);
		}
		
		private void addSuccessor(Node aNode)
		{
			itsSuccessors.add(aNode);
		}
		
		private void addPredecessor(Node aNode)
		{
			itsPredecessors.add(aNode);
		}
		
		public Iterable<Node> getSuccessors()
		{
			return itsSuccessors;
		}
		
		public Iterable<Node> getPredecessors()
		{
			return itsPredecessors;
		}
	}


}
