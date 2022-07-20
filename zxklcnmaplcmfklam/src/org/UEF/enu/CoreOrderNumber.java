package org.UEF.enu;

/**
 * UEFManager에서 사용될 명령번호를 열거형으로 제작한것으로 각 명령은 다음과 같다.<br>
 * <ul>
 * <li>REGIST_LO: 'default' Branch에 Leaf객체 추가</li>
 * <li>REGIST_BO: Branch를 관리 대상으로 추가</li>
 * <li>REGIST_BN: 해당 이름을 가진 Branch를 관리 대상으로 추가</li>
 * <li>REGIST_LOBO: 관리 대상인 Branch에 Leaf객체 추가</li>
 * <li>REGIST_LOBN: 해당 이름을 가진 Branch에 Leaf객체 추가</li>
 * 
 * 
 * <li>REMOVE_LO: 'default' Branch에 Leaf객체 삭제</li>
 * <li>REMOVE_BO: Branch를 관리 대상으로 삭제</li>
 * <li>REMOVE_BN: 해당 이름을 가진 Branch를 관리 대상으로 삭제</li>
 * <li>REMOVE_LOBO: 관리 대상인 Branch에 Leaf객체 삭제</li>
 * <li>REMOVE_LOBN: 해당 이름을 가진 Branch에 Leaf객체 삭제</li>
 * 
 * 
 * <li>SET_FRAME: UEFManager의 기본 프레임 변경</li>
 * <li>SET_STREAM: UEFManager의 관리스레드 출력 스트림 변경</li>
 * 
 * <li>REQUEST_EXECUTE: 패턴과 일치하는 이름을 가진 Branch에 requestExecute실행</li>
 * <li>REQUEST_PAUSE: 패턴과 일치하는 이름을 가진 Branch에 requestPause실행</li>
 * <li>REQUEST_STOP: 패턴과 일치하는 이름을 가진 Branch에 requestStop실행</li>
 * <li>REQUEST_SHUTDOWN: 패턴과 일치하는 이름을 가진 Branch에 requestShutdown실행</li>
 * </ul>
 * */
public enum CoreOrderNumber {
	REGIST_LO(100), REGIST_BO(101), REGIST_BN(102), REGIST_LOBO(103), REGIST_LOBN(104),
	REMOVE_LO(200), REMOVE_BO(201), REMOVE_BN(202), REMOVE_LOBO(203), REMOVE_LOBN(204),
	SET_FRAME(300), SET_STREAM(301), EXIT_UEFMANAGER(302),
	REQUEST_EXECUTE(400), REQUEST_PAUSE(401), REQUEST_STOP(402), REQUEST_SHUTDOWN(403)
	;
	
	int value = 0;
	
	
	CoreOrderNumber(int value) {
		this.value = value;
	}
	
	CoreOrderNumber() {
	}
	
	public int getValue() {
		return value;
	}
}
