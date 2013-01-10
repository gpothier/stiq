package cl.inria.stiq.db;

import zz.utils.bit.BitUtils;
import zz.utils.tree.SimpleTree;
import cl.inria.stiq.db.file.classic.ClassicPagedFile;
import cl.inria.stiq.utils.ConfigUtils;

public class DBConfig {
	/**
	 * Maximum number of event types 
	 */
	public static final int STRUCTURE_TYPE_COUNT = 40000;

	/**
	 * Maximum number of threads
	 */
	public static final int STRUCTURE_THREADS_COUNT = 10000;

	/**
	 * Maximum number of different depths
	 */
	public static final int STRUCTURE_DEPTH_RANGE = 4096;
	
	/**
	 * Maximum number of bytecode locations
	 */
	public static final int STRUCTURE_BYTECODE_LOCS_COUNT = 65536;

	/**
	 * Maximum number of advice source ids
	 */
	public static final int STRUCTURE_ADVICE_SRC_ID_COUNT = 10000;
	
	/**
	 * Maximum number of bytecode roles
	 */
	public static final int STRUCTURE_ROLE_COUNT = 10;
	
	/**
	 * Maximum number of behaviors
	 */
	public static final int STRUCTURE_BEHAVIOR_COUNT = 200000;

	/**
	 * Maximum number of fields
	 */
	public static final int STRUCTURE_FIELD_COUNT = 100000;

	/**
	 * Maximum number of variable indexes
	 */
	public static final int STRUCTURE_VAR_COUNT = 1000;

	/**
	 * Maximum number of objects 
	 */
	public static final int STRUCTURE_OBJECT_COUNT = BitUtils.pow2i(14);
	
	/**
	 * Maximum number of array indexes.
	 */
	public static final int STRUCTURE_ARRAY_INDEX_COUNT = BitUtils.pow2i(14);
	
	/**
	 * Size of file pages in the database
	 */
	public static final int DB_PAGE_SIZE = 4096;
	
	/**
	 * Average event size.
	 */
	public static final int DB_AVG_EVENT_SIZE = 55;
	
	public static final int DB_AVG_EVENTS_PER_PAGE = DB_PAGE_SIZE/DB_AVG_EVENT_SIZE;
	
	/**
	 * Maximum number of index levels for {@link HierarchicalIndex}.
	 */
	public static final int DB_MAX_INDEX_LEVELS = 6;
	
	/**
	 * Maximum size allocated to page buffers.
	 * See {@link ClassicPagedFile.PageDataManager}
	 */
	public static final long DB_PAGE_BUFFER_SIZE = 
		ConfigUtils.readSize("page-buffer-size", getDefaultPageBufferSize());
	
	public static final int DB_THREADS =
		ConfigUtils.readInt("db-threads", getDefaultDbThreads());
	
	/**
	 * Size of database tasks, ie number of primitive operations
	 * they contain.
	 * See eg. {@link SimpleTree#addAsync(long)}.
	 */
	public static final int DB_TASK_SIZE =
		ConfigUtils.readInt("db-task-size", 1024);
	
	/**
	 * Number of times a page must be used before its usage is informed
	 * to the page replacement algorithm.
	 */
	public static final int DB_USE_THRESHOLD =
		ConfigUtils.readInt("db-use-threshold", 10);
	
	private static String getDefaultPageBufferSize()
	{
		int theSize = (int) (Runtime.getRuntime().maxMemory() / (1024*1024));
//		int theSize = (int) (VM.maxDirectMemory() / (1024*1024));
		int theBufferSize = theSize * 6 / 10;
		return theBufferSize + "m";
	}
	
	/**
	 * Default number of database threads to use.
	 */
	private static int getDefaultDbThreads()
	{
		return Runtime.getRuntime().availableProcessors();
	}

}
