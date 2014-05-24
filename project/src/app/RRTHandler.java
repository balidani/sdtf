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

package app;


import java.io.IOException;

import javax.swing.SwingUtilities;

/**
 * Class that reads from the keyboard and generates events to the appia Channel.
 * 
 * @author nuno
 */

public class RRTHandler extends Thread {
	public static Drawer drawer;
	public static Tree tree;
	private java.io.BufferedReader keyb;
	private RRTGenerator generator;
	
	public RRTHandler() {
		super();
		keyb = new java.io.BufferedReader(new java.io.InputStreamReader(System.in));

	}

	public void run() {
		
		System.err.println("Starting RRT Handler, press enter to continue...");
		try {
			keyb.readLine();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		
		generator = new RRTGenerator("points.txt");
		tree = generator.tree;
		
//		SwingUtilities.invokeLater(new Runnable() {
//            @Override
//            public void run() {
//
//                drawer = new Drawer(tree);
//                drawer.setVisible(true);
//            }
//        });
		
		generator.generate();
	}
	
	public void notifyDecision(int decision) {
		synchronized (generator) {

			generator.decision = decision;
			generator.notify();
		}
	}
}
