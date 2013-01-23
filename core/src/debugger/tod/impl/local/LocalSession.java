/*
TOD - Trace Oriented Debugger.
Copyright (c) 2006-2008, Guillaume Pothier
All rights reserved.

This program is free software; you can redistribute it and/or 
modify it under the terms of the GNU General Public License 
version 2 as published by the Free Software Foundation.

This program is distributed in the hope that it will be useful, 
but WITHOUT ANY WARRANTY; without even the implied warranty of 
MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU 
General Public License for more details.

You should have received a copy of the GNU General Public License 
along with this program; if not, write to the Free Software 
Foundation, Inc., 59 Temple Place, Suite 330, Boston, 
MA 02111-1307 USA

Parts of this work rely on the MD5 algorithm "derived from the 
RSA Data Security, Inc. MD5 Message-Digest Algorithm".
*/
package tod.impl.local;

import java.net.URI;

import javax.swing.JComponent;

import cl.inria.stiq.IGUIManager;
import cl.inria.stiq.db.DebugFlags;
import cl.inria.stiq.db.structure.impl.HostInfo;
import cl.inria.stiq.db.structure.impl.StructureDatabase;
import cl.inria.stiq.server.TODServer;
import cl.inria.stiq.transport.ILogCollector;

import tod.core.config.TODConfig;
import tod.core.database.browser.ILogBrowser;
import tod.core.database.structure.IMutableStructureDatabase;
import tod.core.session.AbstractSession;
import tod.utils.PrintThroughCollector;
import zz.utils.properties.IRWProperty;

public class LocalSession extends AbstractSession 
{
	private TODServer itsServer;
	private IMutableStructureDatabase itsStructureDatabase;
	
	private LocalBrowser itsBrowser;
	
	public LocalSession(IGUIManager aGUIManager, URI aUri, TODConfig aConfig)
	{
		super(aGUIManager, aUri, aConfig);
		itsStructureDatabase = StructureDatabase.create(aConfig, false);
		itsBrowser = new LocalBrowser(this, itsStructureDatabase);
		
		itsServer = TODServer.getFactory(aConfig).create(
				aConfig,
				itsStructureDatabase,
				createCollector());
	}
	
	public void disconnect()
	{
		itsServer.close();
	}
	
	public void flush()
	{
		// Nothing to do here.
	}

	public ILogBrowser getLogBrowser()
	{
		return itsBrowser;
	}
	
	public JComponent createConsole()
	{
		return null;
	}
	
	public boolean isAlive()
	{
		return true;
	}

	private ILogCollector createCollector()
	{
		HostInfo theHost = new HostInfo(-1);
		ILogCollector theCollector = new LocalCollector(
				itsBrowser,
				theHost);
		
		if (DebugFlags.COLLECTOR_LOG) theCollector = new PrintThroughCollector(
				theHost,
				theCollector,
				getLogBrowser().getStructureDatabase());
		
		return theCollector;
	}

	public IRWProperty<Boolean> pCaptureEnabled()
	{
		return itsServer.pCaptureEnabled();
	}
}
