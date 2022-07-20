package org.UEF.tests;

import org.UEF.core.UEFLeaf;

public class SimpleLeaf extends UEFLeaf {
	int frameCounter = 0;
	
	long initTime = 0;
	
	public SimpleLeaf() {
		super("i'm tester");
	}

	@Override
	public void init() {
		System.out.println("-----------init-----------");
		initTime = System.currentTimeMillis();
	}

	@Override
	public void ready() {
		System.out.println("-----------ready-----------");

	}

	@Override
	public void execute() {
		System.out.println("-----------execute-----------CurrentFrame: "+getRealFrame());

		frameCounter++;
		
		if(frameCounter == 600) requestShutdown();
	}

	@Override
	public void pause() {
		System.out.println("-----------pause-----------");

	}

	@Override
	public void stop() {
		System.out.println("-----------stop-----------");

	}

	@Override
	public void destroy() {
		System.out.println("-----------destroy-----------");
		
		System.out.println(System.currentTimeMillis() - initTime);
	}

}
