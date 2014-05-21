/*
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

package tfsd.consensus;

import java.net.SocketAddress;
import java.util.HashMap;
import java.util.Random;

import net.sf.appia.core.AppiaEventException;
import net.sf.appia.core.Channel;
import net.sf.appia.core.Direction;
import net.sf.appia.core.Event;
import net.sf.appia.core.Layer;
import net.sf.appia.core.Session;
import net.sf.appia.core.events.SendableEvent;
import tfsd.ProcessInitEvent;
import tfsd.SampleAppl;
import tfsd.SampleApplSession;
import tfsd.SampleProcess;
import tfsd.lrb.ProcessSet;

/**
 * Session implementing the Randomized Consensus Protocol
 * 
 */
public class ConsensusSession extends Session {

	public static final int PHASE_1 = 1;
	public static final int PHASE_2 = 2;
	public static final int PHASE_DECIDE = 3;

	private ProcessSet processes;

	private HashMap<Integer, HashMap<Integer, ProposeEvent>> phaseOneQuorum;
	private HashMap<Integer, HashMap<Integer, ProposeEvent>> phaseTwoQuorum;

	private int startedTimestamp;
	private int decidedTimestamp;

	/**
	 * Builds a new ConsensusSession.
	 * 
	 * @param layer
	 */
	public ConsensusSession(Layer layer) {
		super(layer);

		phaseOneQuorum = new HashMap<Integer, HashMap<Integer, ProposeEvent>>();
		phaseTwoQuorum = new HashMap<Integer, HashMap<Integer, ProposeEvent>>();

		startedTimestamp = 0;
		decidedTimestamp = 0;
	}

	/**
	 * Handles incoming events.
	 * 
	 * @see appia.Session#handle(appia.Event)
	 */
	public void handle(Event event) {

		try {
			if (event instanceof DecideEvent) {
				handleDecide((DecideEvent) event);
			} else if (event instanceof ProposeEvent) {
				handlePropose((ProposeEvent) event);
			} else if (event instanceof SendableEvent) {
				handleSendable((SendableEvent) event);
			} else if (event instanceof ProcessInitEvent) {
				handleProcessInitEvent((ProcessInitEvent) event);
			} else {
				event.go();
			}
		} catch (AppiaEventException e) {
			e.printStackTrace();
		}
	}

	private void handleProcessInitEvent(ProcessInitEvent event)
			throws AppiaEventException {

		processes = event.getProcessSet();
		event.go();
	}

	private synchronized void handleSendable(SendableEvent event)
			throws AppiaEventException {

		String proposed = event.getMessage().popString().trim();
		System.out.println("RC: Received proposed value: " + proposed);

		// Convert to integer
		int proposedInteger = 0;
		try {
			proposedInteger = Integer.parseInt(proposed);
		} catch (NumberFormatException ex) {
			System.out.println("RC: Error parsing proposed value");
		}

		startedTimestamp++;

		sendProposal(event.getSourceSession(), event.getChannel(), PHASE_1,
				proposedInteger, event.getDir());

	}

	private synchronized void handleDecide(DecideEvent event) {

		if (event.getDir() == Direction.UP) {

			int timestamp = event.getMessage().popInt();
			int value = event.getMessage().popInt();

			if (timestamp == startedTimestamp && timestamp > decidedTimestamp) {
				System.out.printf("*** DECIDING %d *** %d, %d, %d\n", value,
						timestamp, startedTimestamp, decidedTimestamp);

				decidedTimestamp++;
				SampleApplSession.instance.decide(value);
			}
		}
	}

	private synchronized void handlePropose(ProposeEvent event)
			throws AppiaEventException {

		// When we receive the proposal, we must broadcast it
		if (event.getDir() == Direction.DOWN) {
			event.go();
			return;
		}

		// Set the value on the proposed object, for convenience
		event.setTimestamp(event.getMessage().popInt());
		event.setValue(event.getMessage().popInt());
		event.setPhase(event.getMessage().popInt());

		// Ignore events with a lower timestamp than the current one
		if (event.getTimestamp() <= decidedTimestamp) {
			// System.out.printf("RC: Timestamp %d was already decided, aborting (%d)\n",
			// event.getTimestamp(), event.getPhase());
			return;
		}

		// Check the phase for the Proposal
		if (event.getPhase() == PHASE_1) {
			handleProposePhase1(event);

		} else if (event.getPhase() == PHASE_2) {
			handleProposePhase2(event);
		}
	}

	private synchronized void handleProposePhase1(ProposeEvent event)
			throws AppiaEventException {

		// First we must wait for a quorum

		// Save each incoming value according to its process id
		SampleProcess pi = processes.getProcess((SocketAddress) event.source);
		int processId = pi.getProcessNumber();

		int ts = event.getTimestamp();

		if (!phaseOneQuorum.containsKey(ts)) {
			HashMap<Integer, ProposeEvent> newQuorum = new HashMap<Integer, ProposeEvent>();
			phaseOneQuorum.put(ts, newQuorum);
		}

		HashMap<Integer, ProposeEvent> tsQuorum = phaseOneQuorum.get(ts);
		tsQuorum.put(processId, event);

		System.out.printf("RC: (PHASE 1) Received incoming ProposeEvent: %d @%d, from %d\n",
			event.getValue(), event.getTimestamp(), processId);

		// When we get a majority, check if the values are identical
		if (tsQuorum.size() > processes.getSize() / 2) {

			ProposeEvent firstProposal = tsQuorum.values().iterator().next();
			int firstValue = firstProposal.getValue();
			boolean identicalValues = true;
			for (ProposeEvent proposed : tsQuorum.values()) {
				if (proposed.getValue() != firstValue) {
					identicalValues = false;
					break;
				}
			}

			// If the values are identical, broadcast this v* value and enter
			// phase 2
			// Otherwise broadcast a default value and enter phase 2
			int broadcastedValue;

			if (identicalValues) {
				System.out
						.printf("RC: (PHASE 1) A quorum was found with identical values (%d)\n",
								firstValue);
				broadcastedValue = firstValue;
			} else {
				System.out
						.printf("RC: (PHASE 1) A quorum was found with different values: [");
				for (ProposeEvent proposed : tsQuorum.values()) {
					System.out.printf("%d, ", proposed.getValue());
				}
				System.out.println("]");

				// TODO: add a flag instead
				broadcastedValue = -1;
			}

			// Send the broadcast for phase 2
			sendProposal(this, event.getChannel(), PHASE_2, broadcastedValue,
					Direction.DOWN);
		}
	}

	private synchronized void handleProposePhase2(ProposeEvent event)
			throws AppiaEventException {

		// Save each incoming value according to its process id
		SampleProcess pi = processes.getProcess((SocketAddress) event.source);
		int processId = pi.getProcessNumber();

		int ts = event.getTimestamp();

		if (!phaseTwoQuorum.containsKey(ts)) {
			HashMap<Integer, ProposeEvent> newQuorum = new HashMap<Integer, ProposeEvent>();
			phaseTwoQuorum.put(ts, newQuorum);
		}

		HashMap<Integer, ProposeEvent> tsTwoQuorum = phaseTwoQuorum.get(ts);
		HashMap<Integer, ProposeEvent> tsOneQuorum = phaseOneQuorum.get(ts);
		tsTwoQuorum.put(processId, event);

		System.out
				.printf("RC: (PHASE 2) Received incoming ProposeEvent: %d @%d, from %d\n",
						event.getValue(), event.getTimestamp(), processId);

		// When we get a quorum, check if the values are identical
		if (tsTwoQuorum.size() == processes.getSize()
				- SampleAppl.TOLERATED_FAILURES) {

			System.out.print("RC: Got a quorum for phase 2 [");
			for (ProposeEvent proposed : tsTwoQuorum.values()) {
				System.out.print(proposed.getValue() + ", ");
			}
			System.out.println("]");

			// Try to find f+1 values of v*
			int count = 0;
			int vStar = -1;

			for (ProposeEvent proposed : tsTwoQuorum.values()) {
				if (proposed.getValue() != -1) {
					vStar = proposed.getValue();
					count++;
				}
			}

			if (count > SampleAppl.TOLERATED_FAILURES) {
				// Found f+1 processes proposing v*, _reliable_ broadcast
				// and decide

				tsTwoQuorum.clear();
				tsOneQuorum.clear();

				System.out.println("RC: (PHASE 2) starting decide with " + vStar);
				sendDecision(this, event.getChannel(), PHASE_DECIDE, vStar,
						Direction.DOWN);

			} else if (count > 0) {
				// Start new round with v*

				tsTwoQuorum.clear();
				tsOneQuorum.clear();
				
				System.out.println("RC: (PHASE 2) starting phase 1 with (star) " + vStar);
				sendProposal(this, event.getChannel(), PHASE_1, vStar,
						Direction.DOWN);

			} else {
				// Start new round with coin toss
				// Pick a random value from the first quorum

				boolean foundProposal = false;
				int randomValue = tsOneQuorum.values().iterator().next()
						.getValue();

				while (!foundProposal) {
					int randomIndex = (new Random()).nextInt()
							% tsOneQuorum.size();
					if (tsOneQuorum.containsKey(randomIndex)) {
						ProposeEvent randomProposal = (ProposeEvent) tsOneQuorum
								.get(randomIndex);
						randomValue = randomProposal.getValue();
						foundProposal = true;
					}
				}

				tsTwoQuorum.clear();
				tsOneQuorum.clear();


				System.out.println("RC: (PHASE 2) starting phase 1 with (random) " + randomValue);
				sendProposal(this, event.getChannel(), PHASE_1, randomValue,
						Direction.DOWN);
			}
		}
	}

	/*
	 * Utils
	 */

	private synchronized void sendDecision(Session session, Channel channel,
			int phase, int vStar, int down) throws AppiaEventException {

		DecideEvent decision = new DecideEvent();
		decision.getMessage().pushInt(phase);
		decision.getMessage().pushInt(vStar);
		decision.getMessage().pushInt(startedTimestamp);

		decision.setSourceSession(session);
		decision.setChannel(channel);
		decision.setDir(Direction.DOWN);

		decision.init();
		decision.go();
	}

	private synchronized void sendProposal(Session source, Channel channel,
			int phase, int value, int dir) throws AppiaEventException {

		ProposeEvent proposal = new ProposeEvent();
		proposal.getMessage().pushInt(phase);
		proposal.getMessage().pushInt(value);
		proposal.getMessage().pushInt(startedTimestamp);

		proposal.setSourceSession(source);
		proposal.setChannel(channel);
		proposal.setDir(dir);

		proposal.init();
		proposal.go();
	}

}
