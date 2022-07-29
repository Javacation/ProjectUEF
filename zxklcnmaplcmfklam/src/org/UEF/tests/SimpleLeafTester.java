package org.UEF.tests;

import org.UEF.core.UEFManager;

public class SimpleLeafTester {

	public static void main(String[] args) {
		SimpleLeaf sl = new SimpleLeaf();
		
		//System.out.println(sl.getParent());
		
		UEFManager.getInstance().launch();
		
		UEFManager.getInstance().regist(sl);
		
		UEFManager.getInstance().requestExecute();
		
		try {
			Thread.sleep(21000);
		}
		catch (Exception e) {
			e.printStackTrace();
		}
		finally {
			UEFManager.getInstance().exit(true);
		}
	}

}
