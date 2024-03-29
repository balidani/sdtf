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

import java.net.InetSocketAddress;

import net.sf.appia.core.AppiaEventException;
import net.sf.appia.core.Channel;
import net.sf.appia.core.Direction;
import net.sf.appia.core.Event;
import net.sf.appia.core.Layer;
import net.sf.appia.core.Session;
import net.sf.appia.core.events.channel.ChannelClose;
import net.sf.appia.core.events.channel.ChannelInit;
import net.sf.appia.core.message.Message;
import net.sf.appia.protocols.common.RegisterSocketEvent;
import tfsd.lrb.ProcessSet;
import tfsd.pfd.PFDStartEvent;
import app.RRTHandler;

/**
 * Session implementing the sample application.
 * 
 * @author nuno
 */
public class SampleApplSession extends Session {

//	public static Channel bebChannel;
	public static Channel rbChannel;
	
	private ProcessSet processes;
	private RRTHandler rrtHandler;

	public static SampleApplSession instance;
	
	public SampleApplSession(Layer layer) {
		super(layer);
		
		instance = this;
	}

	public void init(ProcessSet processes) {
		this.processes = processes;
	}

	public void handle(Event event) {
		System.err.println("Received event: " + event.getClass().getName());
		if (event instanceof SampleSendableEvent)
			handleSampleSendableEvent((SampleSendableEvent) event);
		else if (event instanceof ChannelInit)
			handleChannelInit((ChannelInit) event);
		else if (event instanceof ChannelClose)
			handleChannelClose((ChannelClose) event);
		else if (event instanceof RegisterSocketEvent)
			handleRegisterSocket((RegisterSocketEvent) event);
	}

	/**
	 * @param event
	 */
	private void handleRegisterSocket(RegisterSocketEvent event) {
		if (event.error) {
			System.err.println("Address already in use!");
			System.exit(2);
		}
	}

	/**
	 * @param init
	 */
	private void handleChannelInit(ChannelInit init) {
		try {
			init.go();
		} catch (AppiaEventException e) {
			e.printStackTrace();
		}
		
		Channel channel = init.getChannel();

		try {
			// sends this event to open a socket in the layer that is used has
			// perfect
			// point to point
			// channels or unreliable point to point channels.
			RegisterSocketEvent rse = new RegisterSocketEvent(channel,
					Direction.DOWN, this);
			rse.port = ((InetSocketAddress) processes.getSelfProcess()
					.getSocketAddress()).getPort();
			rse.localHost = ((InetSocketAddress) processes.getSelfProcess()
					.getSocketAddress()).getAddress();
			rse.go();
			ProcessInitEvent processInit = new ProcessInitEvent(channel,
					Direction.DOWN, this);
			processInit.setProcessSet(processes);
			processInit.go();
		} catch (AppiaEventException e1) {
			e1.printStackTrace();
		}

		String channelID = channel.getChannelID();
			
		if (channelID.equals("rbChannel")) {

			System.err.println("RB channel is open.");
			rbChannel = channel;

			// starts the thread that generates the tree
			
			rrtHandler = new RRTHandler();
			rrtHandler.start();
		}
	}

	/**
	 * @param close
	 */
	private void handleChannelClose(ChannelClose close) {
		
		rbChannel = null;
		System.err.println("RB Channel is closed");
	}

	/**
	 * @param event
	 */
	private void handleSampleSendableEvent(SampleSendableEvent event) {
		if (event.getDir() == Direction.DOWN)
			handleOutgoingEvent(event);
		else
			handleIncomingEvent(event);
	}

	/**
	 * @param event
	 */
	private void handleIncomingEvent(SampleSendableEvent event) {
		String message = event.getMessage().popString();
		System.err.print("Received event with message: " + message + "\n>");
	}

	/**
	 * @param event
	 */
	private void handleOutgoingEvent(SampleSendableEvent event) {
		String command = event.getCommand();

		if ("propose".equals(command)) {
			handleSendable(event);
		} else if ("startpfd".equals(command)) {
			handleStartPFD(event);
		} else if ("help".equals(command)) {
			printHelp();
		} else {
			System.err.println("Invalid command: " + command);
			printHelp();
		}
	}

	/**
	 * @param event
	 */
	private void handleStartPFD(SampleSendableEvent event) {
		try {
			PFDStartEvent pfdStart = new PFDStartEvent(rbChannel, Direction.DOWN, this);
			pfdStart.go();
		} catch (AppiaEventException e) {
			e.printStackTrace();
		}
	}

	private void handleSendable(SampleSendableEvent event) {

		try {
			event.go();
		} catch (AppiaEventException e) {
			e.printStackTrace();
		}
	}
	
	public void decide(int ts, int v) {
		
		System.out.printf("(%d, %d) ", ts, v);
		rrtHandler.notifyDecision(v);
	}

	private void printHelp() {
		System.err.println("Available commands:\n"
			+ "propose <value> - Proposes a value for consensus\n"
			+ "startpfd - starts the Perfect Failure detector (when it applies)\n"
			+ "help - Print this help information.");
	}

}
