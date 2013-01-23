/*
 * Created on Apr 23, 2009
 */
package cl.inria.stiq.server;

import cl.inria.stiq.config.TODConfig;
import cl.inria.stiq.db.structure.IMutableStructureDatabase;
import cl.inria.stiq.instrumenter.ASMInstrumenter2;
import cl.inria.stiq.transport.ILogCollector;

public class JavaTODServerFactory2 implements ITODServerFactory
{
	public TODServer create(TODConfig aConfig, IMutableStructureDatabase aStructureDatabase, ILogCollector aLogCollector)
	{
		ASMInstrumenter2 theInstrumenter = new ASMInstrumenter2(aConfig, aStructureDatabase);
		return new JavaTODServer(aConfig, theInstrumenter, aStructureDatabase, aLogCollector);
	}
}
