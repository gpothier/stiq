/*
 * Created on Apr 23, 2009
 */
package cl.inria.stiq.server;

import java.net.Socket;

import zz.utils.properties.IRWProperty;
import cl.inria.stiq.config.TODConfig;

public class JavaTODServer2 extends TODServer
{
	public JavaTODServer2(TODConfig aConfig)
	{
		super(aConfig);
	}

	@Override
	public IRWProperty<Boolean> pCaptureEnabled()
	{
		throw new RuntimeException("Not yet implemented");
	}

	@Override
	protected void accepted(Socket aSocket)
	{
		throw new RuntimeException("Not yet implemented");
	}

}
