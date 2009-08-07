/*
 * Created on Dec 14, 2008
 */
package java.tod;

import java.lang.reflect.Field;
import java.tod.transport.LowLevelEventWriter;
import java.tod.transport._ObjectId;
import java.tod.util._IdentityHashMap;

import tod.agent.ObjectValue;
import tod.agent.ObjectValue.FieldValue;
import tod.agent.util._ArrayList;


/**
 * This is part of a trick to avoid loading _IdentityHashMap and similar
 * in non-agent VMs.
 * @author gpothier
 */
public class ObjectValueFactory
{
	/**
	 * Converts an object to an {@link ObjectValue}, using reflection to obtain field values.
	 */
	private static ObjectValue toObjectValue(Object aOriginal, int aDepth, Object aObject, Mapping aMapping)
	{
		Class<?> theClass = aObject.getClass();
		ObjectValue theResult = new ObjectValue(theClass.getName(), aObject instanceof Throwable);
		aMapping.put(aObject, theResult);
		
		_ArrayList<FieldValue> theFieldValues = new _ArrayList<FieldValue>();
		
		while (theClass != null)
		{
			Field[] theFields = theClass.getDeclaredFields();
			for (Field theField : theFields)
			{
				boolean theWasAccessible = theField.isAccessible();
				theField.setAccessible(true);

				Object theValue;
				try
				{
					theValue = theField.get(aObject);
				}
				catch (Exception e)
				{
					theValue = "Cannot obtain field value: "+e.getMessage();
				}
				
				theField.setAccessible(theWasAccessible);
				
				FieldValue theFieldValue = new FieldValue(theField.getName());
				convert(aOriginal, aDepth+1, theValue, aMapping, theFieldValue);
				
				theFieldValues.add(theFieldValue);
			}
			
			theClass = theClass.getSuperclass();
		}

		theResult.setFields(theFieldValues.toArray(new FieldValue[theFieldValues.size()]));
		
		return theResult;
	}
	
	/**
	 * Ensures that the specified object graph is portable, converting nodes to {@link ObjectValue}
	 * as needed.
	 */
	public static Object convert(Object aObject)
	{
		Mapping theMapping = new Mapping();
		FieldValue theResultHolder = new FieldValue(null);
		convert(aObject, 0, aObject, theMapping, theResultHolder);
		theMapping.resolve();
		return theResultHolder.value;
	}
	
	private static void convert(Object aOriginal, int aDepth, Object aObject, Mapping aMapping, FieldValue aTarget)
	{
		if (aDepth > 20)
		{
			System.out.println("ObjectValueFactory.convert() - big - "+aObject.getClass()+" "+aObject+" "+aOriginal);
		}
		
		if (aObject == null) aTarget.setValue(null);
		else if (isPortable(aObject)) aTarget.setValue(aObject);
		else if (! LowLevelEventWriter.shouldSendByValue(aObject)) aTarget.setValue(new _ObjectId(ObjectIdentity.get(aObject)));
		else 
		{
			ObjectValue theObjectValue = aMapping.get(aObject);
			if (theObjectValue != null) aTarget.setValue(theObjectValue);
			else if (aMapping.isProcessing(aObject)) aMapping.register(aTarget, aObject);
			else
			{
				aMapping.processing(aObject);
				theObjectValue = toObjectValue(aOriginal, aDepth, aObject, aMapping);
				aTarget.setValue(theObjectValue);
			}
		}
	}

	private static boolean isPortable(Object aObject)
	{
		return (aObject instanceof String) || (aObject instanceof Number) || (aObject instanceof Boolean);
	}
	
	private static class Mapping
	{
		private _IdentityHashMap<Object, ObjectValue> itsMap = new _IdentityHashMap<Object, ObjectValue>();
		private _ArrayList<FieldResolver> itsResolvers = new _ArrayList<FieldResolver>();
		
		public void processing(Object aKey)
		{
			itsMap.put(aKey, null);
		}
		
		public boolean isProcessing(Object aKey)
		{
			return itsMap.containsKey(aKey);
		}
		
		public void register(FieldValue aTarget, Object aKey)
		{
			itsResolvers.add(new FieldResolver(aTarget, aKey));
		}
		
		public void put(Object aKey, ObjectValue aValue)
		{
			itsMap.put(aKey, aValue);
		}
		
		public ObjectValue get(Object aKey)
		{
			return itsMap.get(aKey);
		}
		
		public void resolve()
		{
			for (int i=0;i<itsResolvers.size();i++) 
			{
				FieldResolver theResolver = itsResolvers.get(i);
				theResolver.resolve(this);
			}
		}
		
	}
	
	private static class FieldResolver
	{
		private final FieldValue itsFieldValue;
		private final Object itsKey;

		public FieldResolver(FieldValue aFieldValue, Object aKey)
		{
			itsFieldValue = aFieldValue;
			itsKey = aKey;
		}
		
		public void resolve(Mapping aMapping)
		{
			itsFieldValue.setValue(aMapping.get(itsKey));
		}
	}
}
