/*
 *
 * Hands-On code of the book Introduction to Reliable Distributed Programming
 * by Christian Cachin, Rachid Guerraoui and Luis Rodrigues
 * Copyright (C) 2005-2011 Luis Rodrigues
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 2.1 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301, USA
 *
 * Contact
 * 	Address:
 *		Rua Alves Redol 9, Office 605
 *		1000-029 Lisboa
 *		PORTUGAL
 * 	Email:
 * 		ler@ist.utl.pt
 * 	Web:
 *		http://homepages.gsd.inesc-id.pt/~ler/
 * 
 */

package tfsd;

import tfsd.pfd.*;

import net.sf.appia.core.Layer;
import net.sf.appia.core.Session;
import net.sf.appia.core.events.channel.ChannelClose;
import net.sf.appia.core.events.channel.ChannelInit;
import net.sf.appia.protocols.common.RegisterSocketEvent;

/**
 * Layer of the application protocol.
 * 
 * @author nuno
 */
public class SampleApplLayer extends Layer {

  public SampleApplLayer() {
    /* events that the protocol will create */
    evProvide = new Class[4];
    evProvide[0] = ProcessInitEvent.class;
    evProvide[1] = RegisterSocketEvent.class;
    evProvide[2] = SampleSendableEvent.class;
    evProvide[3] = PFDStartEvent.class;

    /*
     * events that the protocol require to work. This is a subset of the
     * accepted events
     */
    evRequire = new Class[1];
    evRequire[0] = ChannelInit.class;

    /* events that the protocol will accept */
    evAccept = new Class[4];
    evAccept[0] = ChannelInit.class;
    evAccept[1] = ChannelClose.class;
    evAccept[2] = RegisterSocketEvent.class;
    evAccept[3] = SampleSendableEvent.class;
  }

  /**
   * Creates a new session to this protocol.
   * 
   * @see appia.Layer#createSession()
   */
  public Session createSession() {
    return new SampleApplSession(this);
  }

}
