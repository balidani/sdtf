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
import net.sf.appia.protocols.common.RegisterSocketEvent;
import tfsd.pfd.PFDStartEvent;

/**
 * Session implementing the sample application.
 * 
 * @author nuno
 */
public class SampleApplSession extends Session {

	Channel channel;
	private ProcessSet processes;
	private SampleApplReader reader;
	private boolean blocked = false;

	public SampleApplSession(Layer layer) {
		super(layer);
	}

	public void init(ProcessSet processes) {
		this.processes = processes;
	}

	public void handle(Event event) {
		System.out.println("Received event: " + event.getClass().getName());
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
			System.out.println("Address already in use!");
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
		channel = init.getChannel();

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
		System.out.println("Channel is open.");
		// starts the thread that reads from the keyboard.
		reader = new SampleApplReader(this);
		reader.start();
	}

	/**
	 * @param close
	 */
	private void handleChannelClose(ChannelClose close) {
		channel = null;
		System.out.println("Channel is closed.");
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
		System.out.print("Received event with message: " + message + "\n>");
	}

	/**
	 * @param event
	 */
	private void handleOutgoingEvent(SampleSendableEvent event) {
		String command = event.getCommand();

		if ("propose".equals(command)) {
			handlePropose(event);
		} else if ("startpfd".equals(command)) {
			handleStartPFD(event);
		} else if ("help".equals(command)) {
			printHelp();
		} else {
			System.out.println("Invalid command: " + command);
			printHelp();
		}
	}

	/**
	 * @param event
	 */
	private void handleStartPFD(SampleSendableEvent event) {
		try {
			PFDStartEvent pfdStart = new PFDStartEvent(channel, Direction.DOWN,
					this);
			pfdStart.go();
		} catch (AppiaEventException e) {
			e.printStackTrace();
		}
	}

	private void handlePropose(SampleSendableEvent event) {
		// TODO: implement

		System.out.println("Sending ProposeEvent");

		try {
			event.go();
		} catch (AppiaEventException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}

	private void printHelp() {
		System.out
				.println("Available commands:\n"
						+ "propose <value> - Proposes a value for consensus\n"
						+ "startpfd - starts the Perfect Failure detector (when it applies)\n"
						+ "help - Print this help information.");
	}

}
