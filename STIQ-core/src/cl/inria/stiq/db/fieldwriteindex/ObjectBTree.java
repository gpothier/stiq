package cl.inria.stiq.db.fieldwriteindex;

import cl.inria.stiq.db.fieldwriteindex.OnDiskIndex.ObjectPageSlot;
import cl.inria.stiq.db.Stats;
import cl.inria.stiq.db.file.InsertableBTree;
import cl.inria.stiq.db.file.IntInsertableBTree;
import cl.inria.stiq.db.file.IntInsertableBTree.IntTuple;
import cl.inria.stiq.db.file.Page.PageIOStream;
import cl.inria.stiq.db.file.Page.PidSlot;
import cl.inria.stiq.db.file.TupleBufferFactory;

public class ObjectBTree extends InsertableBTree<IntTuple>
{
	
	public ObjectBTree(String aName, PidSlot aRootSlot)
	{
		super(aName, Stats.ACC_OBJECTS, aRootSlot);
	}
	
	@Override
	protected TupleBufferFactory<IntTuple> getTupleBufferFactory()
	{
		return IntInsertableBTree.INT_TUPLEFACTORY;
	}

	/**
	 * Returns a slot that can store a pointer to an object page.
	 * This method insert an entry for the given key if there is none.
	 */
	public ObjectPageSlot getSlot(long aKey)
	{
		PageIOStream theStream = insertLeafKey(aKey, true);
		ObjectPageSlot theSlot = new ObjectPageSlot(theStream);
		if (Stats.COLLECT)
		{
			if (theSlot.isNull()) Stats.OBJECT_TREE_ENTRIES++;
		}
		return theSlot;
	}
	
	public int get(long aKey)
	{
		IntTuple theTuple = getTupleAt(aKey, true);
		return theTuple != null ? theTuple.getData() : 0;
	}
}
