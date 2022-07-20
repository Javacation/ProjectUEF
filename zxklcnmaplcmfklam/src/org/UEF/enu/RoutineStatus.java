package org.UEF.enu;

public enum RoutineStatus {
	NEW(0), INIT(1), READY(2), EXECUTE(3), PAUSE(4), STOP(5), DESTROY(6);

	RoutineStatus(int i) {
		value = i;
	}
	
	RoutineStatus() {
		
	}
	
	private int value = -1;
	
	public int getValue() {
		return value;
	}
	
	public static RoutineStatus findByName(String name) {
		RoutineStatus result = null;
		
		for(RoutineStatus temp: RoutineStatus.values()) {
			if(temp.name().equals(name)) {
				result = temp;
				break;
			}
		}
		
		return result;
		
	}
	
	public static RoutineStatus findByValue(int value) {
		RoutineStatus result = null;
		
		for(RoutineStatus temp: RoutineStatus.values()) {
			if(temp.getValue() == value) {
				result = temp;
				break;
			}
		}
		
		return result;
		
	}
}
