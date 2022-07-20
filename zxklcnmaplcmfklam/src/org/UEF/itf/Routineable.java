package org.UEF.itf;


/**
 * Runnable과 같이 사용하여 스레드안에 사이클을 만드는 인터페이스
 * */
public interface Routineable{
	
	/**
	 * 시작시 단 한번만 실행하는 메소드<br>
	 * 실행중 예외 발생시 다음형태로 예외처리 작업이 실행된다.<br>
	 * <b>planner.executePlan(this.getName(), '#'+Exception.class, '#INIT')</b>
	 * <br>실행되는 Plan 결과값에 [notend]가 포함된경우 leaf가 종료되지 않고 다시 루틴으로 복귀함
	 * */
	public void init();
	
	/**
	 * 첫 시작 또는 stop()후 재시작 시 실행하는 메소드<br>
	 * 실행중 예외 발생시 다음형태로 예외처리 작업이 실행된다.<br>
	 * <b>planner.executePlan(this.getName(), '#'+Exception.class, '#READY')</b>
	 * <br>실행되는 Plan 결과값에 [notend]가 포함된경우 leaf가 종료되지 않고 다시 루틴으로 복귀함
	 * */
	public void ready();
	
	/**
	 * 정해놓은 프레임에 한번씩 실행하는 메소드<br>
	 * 실행중 예외 발생시 다음형태로 예외처리 작업이 실행된다.<br>
	 * <b>planner.executePlan(this.getName(), '#'+Exception.class, '#EXECUTE')</b>
	 * <br>실행되는 Plan 결과값에 [notend]가 포함된경우 leaf가 종료되지 않고 다시 루틴으로 복귀함
	 * */
	public void execute();
	
	/**
	 * 잠시 중지할 때 실행하는 메소드로 재시작 시 바로 execute()로 진입<br>
	 * 실행중 예외 발생시 다음형태로 예외처리 작업이 실행된다.<br>
	 * <b>planner.executePlan(this.getName(), '#'+Exception.class, '#PAUSE')</b>
	 * <br>실행되는 Plan 결과값에 [notend]가 포함된경우 leaf가 종료되지 않고 다시 루틴으로 복귀함
	 * */
	public void pause();
	
	/**
	 * 멈출 때 실행하는 메소드로 pause()를 거쳐서 진입함<br>
	 * 실행중 예외 발생시 다음형태로 예외처리 작업이 실행된다.<br>
	 * <b>planner.executePlan(this.getName(), '#'+Exception.class, '#STOP')</b>
	 * <br>실행되는 Plan 결과값에 [notend]가 포함된경우 leaf가 종료되지 않고 다시 루틴으로 복귀함
	 * */
	public void stop();
	
	/**
	 * 스레드를 완전히 종료할 때 실행하는 메소드로 pause(), stop()을 거쳐서 진입함<br>
	 * 실행중 예외 발생시 다음형태로 예외처리 작업이 실행된다.<br>
	 * <b>planner.executePlan(this.getName(), '#'+Exception.class, '#DESTROY')</b>
	 * <br>실행되는 Plan 결과값에 [notend]가 포함된경우 leaf가 종료되지 않고 다시 루틴으로 복귀함
	 * */
	public void destroy();
}
