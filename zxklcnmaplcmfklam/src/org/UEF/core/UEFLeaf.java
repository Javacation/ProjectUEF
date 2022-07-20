package org.UEF.core;

import java.security.AccessControlException;
import java.util.logging.Level;

import org.UEF.enu.RoutineStatus;
import org.UEF.enu.RoutineTriggerStatus;
import org.UEF.itf.Routineable;
/**
 * UEFCell클래스를 상속하여 만든 추상 클래스로 실제로 기능을 실행하는 사이클을 구현해 놨으며
 * 사용자는 해당 클래스를 상속하여 기능만 정의하면 된다.<br>
 * 프레임의 조정을 할 수 있으나 {@link UEFLeaf execute()}를 제외한 메소드는 프레임 설정에 따라 계산을 진행하지 않고 대기없이 가능한 빠르게 처리하도록 구현했다.
 * */
public abstract class UEFLeaf extends UEFCell implements Routineable{
	// 현재 실행 상태 추적을 위한 열거형
	private RoutineStatus currentStatus = RoutineStatus.NEW;
	
	private boolean isInit = false;
	// 프레임 값 변화 감지를 위한 필드
	private long beforeFrame = 0l;
	// 최소실행시간으로 대기시간 계산에 사용됨
	private long waitNanos = 0l;
	// 실제로 메소드가 처리될 때까지 걸린 나노초 (초기화가 안됬을 경우 -1)
	private long[] routineExcutionTimeArray = new long[] {-1l,-1l,-1l,-1l,-1l,-1l,-1l};
	
	/*
	 * [대기시간 오차 조정 계수]
	 * 지정한 프레임을 지키기 위해 RoutineCapsule의 awaitNanos에서 일어나는 시간 오차를 줄이기 위한 실수 배수로
	 * 실제프레임은 현재 Leaf의 프레임에 해당 값을 곱하여 실수형태로 반환한다.
	 * 최소실행시간보다 실제실행시간이 적어 awaitNanos를 진행할 때  awaitNanos에서 발생하는 대기시간 오차를 줄이기 위한 메소드로 
	 * 실제실행시간이 최소실행시간보다 큰경우에는 값을 변경하지 않는다. (프레임 계산이 목적이 아닌 대기시간 오차를 줄이는게 목적)
	 * */
	private double innerAwaitTimeAdjustMultiple = 1.0;
	
	/*
	 * [실제 프레임 계산 계수]
	 * 실행시간을 통해 실제프레임을 계산하여 보여주기 위한 실수 값으로 
	 * 최소실행시간이 실제실행시간 보다 큰경우 [innerAwaitTimeAdjustMultiple]의 값으로 바꾼다.
	 * 최소실행시간이 실제실행시간 보다 작거나 같은경우 [최소실행시간이 / 실제실행시간]의 값으로 바꾼다.
	 * */
	private double outterAwaitTimeAdjustMultiple = 1.0;
	
	
	/**
	 * UEFLeaf의 생성자로 super(이름, 부모 프레임 사용여부, 부모 로거 사용여부)를 호출한다.
	 * */
	protected UEFLeaf(String name, boolean useParentFrame, boolean useParentLogger) {
		super(name, useParentFrame, useParentLogger);
	}
	
	/**
	 * UEFLeaf의 생성자로 this(이름, false, false)를 호출한다.
	 * */
	protected UEFLeaf(String name) {
		this(name, true, true);
	}
	
	
	@Override
	public void run() {
		// 현재 프레임 저장
		beforeFrame = getFrame();
		
		try {
			//lock.lock();
			getLock().lock();
			
			// NEW인 경우 무한대기
			while(getCurrentTriggerStatus() == RoutineTriggerStatus.NEW) {
				try {
					getCommonCondition(0).awaitUninterruptibly();
				}
				catch (Exception e) {
				}
			}
			
			// SHUTDOWN이 아닌경우 반복
			while(getCurrentTriggerStatus() != RoutineTriggerStatus.SHUTDOWN) {
				
				// init을 한 번이라도 실행하지 않은경우 실행한다.
				if(!isInit) {
					// init 실행요청
					RoutineCapsule(RoutineStatus.INIT);
					isInit = true;
				}
				
				
				// STOP상태에서 복귀한 경우 또는 처음에 진입한경우 READY실행
				if(currentStatus != RoutineStatus.READY) {
					// ready 실행요청
					RoutineCapsule(RoutineStatus.READY);
				}
				
				// START, PAUSE, WAKEUP인 경우 반복
				while(getCurrentTriggerStatus() == RoutineTriggerStatus.EXECUTE ||
						getCurrentTriggerStatus() == RoutineTriggerStatus.PAUSE) {
					
					// START, WAKEUP인 경우 반복
					while(getCurrentTriggerStatus() == RoutineTriggerStatus.EXECUTE) { // EXECUTE인 경우 반복
						
						// execute 실행요청
						RoutineCapsule(RoutineStatus.EXECUTE);
					}
					
					// EXECUTE 종료가 감지되었기에 pause실행, PAUSED상태로 변경
					if(currentStatus != RoutineStatus.PAUSE) {
						// pause 실행요청
						RoutineCapsule(RoutineStatus.PAUSE);
					}
					
					// PAUSE인 경우 대기
					if(getCurrentTriggerStatus() == RoutineTriggerStatus.PAUSE) getCommonCondition(0).awaitUninterruptibly();
				}
				
				// STOP, SHUTDOWN중 하나이므로  stop실행 후 STOPPED상태로 변경
				if(currentStatus != RoutineStatus.STOP) {
					
					// stop 실행요청
					RoutineCapsule(RoutineStatus.STOP);
				}
					
				
				// STOP인 경우 대기
				if(getCurrentTriggerStatus() == RoutineTriggerStatus.STOP) getCommonCondition(0).awaitUninterruptibly();
			}
			
			// SHUTDOWN확인 destroy실행, DESTROYED상태로 변경
			// destroy 실행요청
			RoutineCapsule(RoutineStatus.DESTROY);
			
		}
		catch (Exception e) {
			e.printStackTrace();
		}
		finally {
			getLock().unlock();
		}
	}
	
	/**
	 * Routineable 메소드를 호출하는 메소드로 실행할 메소드에 따라 값을 다르게 넣으면 된다.<br>
	 * 그리고 각 메소드가 실행할 내용을 if, switch를 통해 서술하고 실패에 대한 로깅작업을 진행해야한다.
	 * @param status<ul>
					<li>0 - init</li>
					<li>1 - ready</li>
					<li>2 - execute</li>
					<li>3 - pause</li>
					<li>4 - stop</li>
					<li>5 - destory</li>
					</ul>
	 * */
	private Exception RoutineCapsule(RoutineStatus status) {
		Exception result = null;
		long tempNanos = 0l, frameTime = System.nanoTime();
		int arrayValue = status.getValue();
		
		try {	
			if(beforeFrame != getFrame()) { // 프레임 값이 바뀐경우
				beforeFrame = getFrame(); // 바뀐 값으로 초기화
				innerAwaitTimeAdjustMultiple = 1.0; // 대기시간 오차 조정 계수 초기화
				outterAwaitTimeAdjustMultiple = 1.0; // 실제 프레임 계산 계수 초기화
			}
			
			// 최소 실행시간 업데이트
			// 설정된 프레임만큼 초당 계산을 진행하기위해 
			// 루틴시작부터 종료까지 걸려야하는 최소시간
			waitNanos = UEFCell.ONE_NANO_SECONED / getFrame();
			
			// 시간 측정시작
			tempNanos = System.nanoTime();
			switch(status) {
				case INIT:
					currentStatus = RoutineStatus.INIT;
					init();
					break;
					
				case READY:
					currentStatus = RoutineStatus.READY;
					ready();
					
					break;
					
				case EXECUTE:
					currentStatus = RoutineStatus.EXECUTE;
					execute();
					
					break;
					
				case PAUSE:
					currentStatus = RoutineStatus.PAUSE;
					pause();
					
					break;
					
				case STOP:
					currentStatus = RoutineStatus.STOP;
					stop();
					
					break;
					
				case DESTROY:
					currentStatus = RoutineStatus.DESTROY;
					destroy();
					
					break;
					
					
				default:
					throw new AccessControlException(getName()+"의 잘못된 RoutineCapsule() 접근이 감지되었습니다.");
			}
		}
		catch (Exception e) {
			int excepProcessResult = exceptionProcessing(e, currentStatus);
			
			//log(Level.WARNING, "예외가 발생했습니다. "+e.getMessage()+" ("+e.getClass()+")");
			
			if(excepProcessResult == -1) { // 예외처리를 지정하지 않은경우
				requestShutdown();
			} else if(excepProcessResult == 0) { // 예외처리 성공후 복귀
				// 루틴으로 복귀
			} else if(excepProcessResult == 1) { // 예외처리 성공후 종료
				requestShutdown();
			} else if(excepProcessResult == 2) { // 예외처리 실패후 종료
				requestShutdown();
			} else {
				requestShutdown();
			}
		}
		finally {
			// 프레임 계수를 조정하기 위한 시간측정변수
			long realSleepTime = 0l;
			
			// 루틴 실행시간 측정 종료
			tempNanos = System.nanoTime() - tempNanos;
			
			// 각 루틴 실행시간을 저장
			routineExcutionTimeArray[arrayValue] = tempNanos;
			
			try {
				// 대기시간 = (최소실행시간 - 루틴실행시간) * 오차조정계수
				// 대기분할시간 = 실제대기시간을 10으로 나눈 값 (예를 들어 1_000_000_000/60 => 16_666_666 / 10 => 1_666_666)
				long finalSleepTimeDivide10 = (long)((waitNanos - tempNanos) * innerAwaitTimeAdjustMultiple) / 10;
				// 대기 마감시간
				long deadLine = System.nanoTime() + (long)((waitNanos - tempNanos) * innerAwaitTimeAdjustMultiple);
				
				// (대기분할시간 > 0) 인 경우
				if(finalSleepTimeDivide10 > 0) {
					try {
						// 락을 해제하여 request진입을 허용해 중간에 다른 명령을 내릴 수 있게 한다.
						// condition.awaitNanos를 사용할 경우 대기시간이 정확하게 수행되지 않아 스레드를 슬립시키기로 변경
						// 락 해제
						getLock().unlock();
						
						realSleepTime = System.nanoTime();
						
						do {
							// 더 정확한 대기를 위해 최종대기시간을 10개로 나눠 10번을 대기함
							try {
								// 대기할 밀리초(대기분할시간 / 10 / 1밀리초), 대기할 나노초(대기분할시간 / 10 % 1밀리초)
								Thread.sleep(finalSleepTimeDivide10 / 1_000_000l, (int)(finalSleepTimeDivide10 % 1_000_000l));
							}
							catch (InterruptedException e1) {
								break;
							}
							catch (Exception e2) {
								log(Level.WARNING, "예외가 발생했습니다. "+e2.getMessage()+" ("+e2.getClass()+")");
							}
							// 대기 마감시간을 현재 넘지 못한경우 다시 대기 (만약 넘게됬다면 그 즉시 탈출)
						} while(System.nanoTime() <= deadLine);
						
						realSleepTime = System.nanoTime() - realSleepTime;
						
						// 오차 조정 계수 = 대기시간(대기분할시간 * 10) / (실제 대기시간)
						innerAwaitTimeAdjustMultiple = (double)finalSleepTimeDivide10 * 10 / (realSleepTime);
					}
					catch (Exception e) {
						log(Level.WARNING, "예외가 발생했습니다. "+e.getMessage()+" ("+e.getClass()+")");
					}
					finally {
						// 락 진입
						getLock().lock();
					}
				}
				else {
					
				}
				
				frameTime = System.nanoTime() - frameTime;
				
				// 최종 실행시간과 최소 실행시간이 비슷해야 프레임을 유지할 수 있음
				// 실제 프레임 계산 계수 = 최소 실행시간 / 최종 실행시간(루틴실행부터 대기까지 전부 걸린시간)
				outterAwaitTimeAdjustMultiple = waitNanos / (double)frameTime;
			}
			catch (Exception e) {
				e.printStackTrace();
			}

		}
		
		return result;
	}
	
	

	/**
	 * 각 루틴메소드를 처리하는데 걸린시간
	 * @param status 찾고자 하는 루틴 메소드 이름
	 * @return status와 관련있는 메소드를 실행하는데 걸린 나노초 (프레임아님)
	 * */
	public final long getRoutineExcutionTime(RoutineStatus status) {
		return routineExcutionTimeArray[status.getValue()];
	}
	
	/**
	 * 실제 프레임을 리턴하는 메소드로 프레임을 변경하고 최소 한 번 이상의  {@link UEFLeaf execute()} 계산이 있어야 정확한 프레임을 구할 수 있으며, 
	 * 또한 한 번의 계산을 진행하는데 프레임에 따른 최소실행시간과의 오차가 심한 실행시간을 가지는 경우 제대로된 프레임 값을 보장할 수 없음<br>
	 * (현재의 실행 프레임을 구하므로 프레임간의 실행간격이 일정하지 않을 수 있으며 중간에 루틴의 변경이 잦은경우 정확하지 않을 수 있음)
	 * @return 초당 계산횟수(지정 프레임 * 프레임 계산속도 유지 변수)
	 * */
	public final double getRealFrame() {
		return getFrame() * outterAwaitTimeAdjustMultiple;
	}

	/**
	 * 루틴 진행상태 리턴 메소드
	 * @return 루틴 진행상태
	 * <ul>
	 * <li>NEW - 만들어진 상태</li>
	 * <li>INIT - init()을 실행한 상태</li>
	 * <li>READY - ready()를 실행한 상태</li>
	 * <li>EXECUTE - execute()를 실행한 상태</li>
	 * <li>PAUSE - pause()를 실행한상태</li>
	 * <li>STOP - stop()을 실행한 상태</li>
	 * <li>DESTROY - destroy()를 실행한 상태</li>
	 * </ul>
	 * */
	public final RoutineStatus getCurrentStatus() {
		return currentStatus;
	}
	
	/**
	 * RequestMethod에서 공통으로 처리되는 과정을 묶은 메소드
	 * */
	protected final void commonRequestMethod() {
		freeBlocked();
	}
	
	/**
	 * 각 루틴실행중 예외발생 시 실행시킬 메소드(easy 모드인 경우 사용)
	 * 
	 * @param e 발생한 예외
	 * @param where 예외가 발생한 위치
	 * <ul>
	 * <li>INIT - init() 실행중 발생한 경우</li>
	 * <li>READY - ready() 실행중 발생한 경우</li>
	 * <li>EXECUTE - execute() 실행중 발생한 경우</li>
	 * <li>PAUSE - pause() 실행중 발생한 경우</li>
	 * <li>STOP - stop() 실행중 발생한 경우</li>
	 * <li>DESTROY - destroy() 실행중 발생한 경우</li>
	 * </ul>
	 * 
	 * @return 
	 * <ul>
	 * <li>-1 - 예외처리를 지정하지 않은경우 발생</li>
	 * <li>0 - 예외처리를 성공후 루틴으로 복귀</li>
	 * <li>1 - 예외처리를 성공후 종료</li>
	 * <li>2 - 예외처리를 실패한 경우</li>
	 * </ul>
	 * */
	public int exceptionProcessing(Exception e, RoutineStatus where) {
		int ret = -1;
		
		log(Level.WARNING, "exceptionProcessing를 지정하지 않았습니다."
				+ "\n발생한 예외: "+e.getMessage()
				+ "\n발생한 예외 클래스: "+e.getClass()
				+ "\n발생한 클래스: "+this.getClass()
				+ "\n발생한 루틴 위치: "+where);
		
		return ret;
	}
	
	@Override
	public final void requestPause() {
		super.requestPause();
		
		try {
			if(getCurrentTriggerStatus() == RoutineTriggerStatus.EXECUTE || 
					getCurrentTriggerStatus() == RoutineTriggerStatus.NEW) {
				commonRequestMethod();
				
				getLock().lock();
				setTrigger(RoutineTriggerStatus.PAUSE);
				getCommonCondition(0).signal();
			}
			else return;
		}
		catch (Exception e) {
			e.printStackTrace();
			
			requestShutdown();
		}
		finally {
			getLock().unlock();
		}
		
		
	}

	@Override
	public final void requestExecute() {
		super.requestExecute();
		
		try {
			if(getCurrentTriggerStatus() == RoutineTriggerStatus.PAUSE ||
					getCurrentTriggerStatus() == RoutineTriggerStatus.STOP ||
					getCurrentTriggerStatus() == RoutineTriggerStatus.NEW) {
				commonRequestMethod();
				
				getLock().lock();
				setTrigger(RoutineTriggerStatus.EXECUTE);
				getCommonCondition(0).signal();
			}
			else return;
		}
		catch (Exception e) {
			e.printStackTrace();
			
			requestShutdown();
		}
		finally {
			getLock().unlock();
		}
		
		
	}

	@Override
	public final void requestStop() {
		super.requestStop();
		
		try {
			if(getCurrentTriggerStatus() == RoutineTriggerStatus.EXECUTE ||
					getCurrentTriggerStatus() == RoutineTriggerStatus.PAUSE ||
					getCurrentTriggerStatus() == RoutineTriggerStatus.NEW) {
				commonRequestMethod();
				
				getLock().lock();
				setTrigger(RoutineTriggerStatus.STOP);
				getCommonCondition(0).signal();
				
			}
			else return;
		}
		catch (Exception e) {
			e.printStackTrace();
			
			requestShutdown();
		}
		finally {
			getLock().unlock();
		}
	}
	
	@Override
	public final void requestShutdown() {
		super.requestShutdown();
		
		try {
			commonRequestMethod();
			
			getLock().lock();
			setTrigger(RoutineTriggerStatus.SHUTDOWN);
			getCommonCondition(0).signal();

		}
		catch (Exception e) {
			e.printStackTrace();
		}
		finally {
			getLock().unlock();
		}
	}
}
