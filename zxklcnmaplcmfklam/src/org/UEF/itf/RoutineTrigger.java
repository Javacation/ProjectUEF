package org.UEF.itf;

import java.security.AccessControlException;

/**
 * Routineable 인터페이스를 구현한 클래스를 조종하기 위한 인터페이스
 * */
public interface RoutineTrigger {
	/*public void requestStart();*/
	
	/**
	 * 일시중지를 요청하는 메소드로 EXECUTE, STOP, NEW 에서 접근가능함 (실행 성공시 UEFCell의 Trigger상태가 PAUSE로 전환)
	 * @throws AccessControlException SHUTDOWN, PAUSE 에서 접근시 예외발생
	 * */
	public void requestPause() throws AccessControlException;
	
	/**
	 * 중지를 요청하는 메소드로 EXECUTE, PAUSE, NEW 에서 접근가능함 (실행 성공시 UEFCell의 Trigger상태가 STOP로 전환)
	 * @throws AccessControlException SHUTDOWN, STOP 에서 접근시 예외발생
	 * */
	public void requestStop() throws AccessControlException;
	
	/**
	 * 실행을 요청하는 메소드로 PAUSE, STOP, NEW 에서 접근가능함 (실행 성공시 UEFCell의 Trigger상태가 EXECUTE로 전환)
	 * @throws AccessControlException SHUTDOWN, EXECUTE 에서 접근시 예외발생
	 * */
	public void requestExecute() throws AccessControlException;
	
	/**
	 * 종료를 요청하는 메소드로 EXECUTE, PAUSE, STOP, NEW 에서 접근가능함 (실행 성공시 UEFCell의 Trigger상태가 SHUTDOWN로 전환)
	 * @throws AccessControlException SHUTDOWN 에서 접근시 예외발생
	 * */
	public void requestShutdown() throws AccessControlException;
}
