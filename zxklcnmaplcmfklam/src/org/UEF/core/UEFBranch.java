package org.UEF.core;

import java.security.AccessControlException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Vector;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;

import org.UEF.enu.RoutineTriggerStatus;


/**
 * UEFCell클래스를 상속하여 만든 추상 클래스로 UEFCell을 가질 수 있는 리스트를 가지고있다.
 * 리스트에 포함된 모든 객체들은 한번에 컨트롤 할 수 있도록 만들어진 추상 클래스이다.<br>
 * 상속받아 리스트에 들어갈 객체들을 필터링하여 그룹화 할 수 있게 만들 수 있다.
 * */
public abstract class UEFBranch extends UEFCell{
	protected List<UEFCell> list = new Vector<UEFCell>();
	// iterator사용중 list에 변화가 생길경우 iterator를 다시 호출할 수 있게 알려주는 값
	private boolean listCouncurrentDanger = false;
	
	public UEFBranch(String name, boolean useParentFrame, boolean useParentLogger) {
		super(name, useParentFrame, useParentLogger);
	}
	
	public UEFBranch(String name) {
		this(name, true, true);
	}
	
	
	@Override
	public void run() {
		try {
			getLock().lock();
			
			while(getCurrentTriggerStatus() != RoutineTriggerStatus.SHUTDOWN) {
				try {
					// 대기중
					getCommonCondition(0).await(500, TimeUnit.MILLISECONDS);
				}
				catch (InterruptedException e1) {
					
				}
				catch (Exception e2) {
					log(Level.WARNING, "예외가 발생했습니다. "+e2.getMessage()+" ("+e2.getClass()+")");
				}
				finally {
					Iterator<UEFCell> itr = list.iterator();
					
					// iterator호출로 안전확보
					listCouncurrentDanger = false;
					
					while(itr.hasNext()) {
						// iterator를 다시 호출해야하는지 확인하는 메소드
						if(listCouncurrentDanger) {
							// 재호출
							itr = list.iterator();
							// iterator호출로 안전확보
							listCouncurrentDanger = false;
							continue;
						}
						
						UEFCell u = itr.next();
						
						// 현재 객체가 null이면 생략
						if(u == null) continue;
												
						// 현재 객체 상태
						RoutineTriggerStatus urts = u.getCurrentTriggerStatus();
						
						if(urts == RoutineTriggerStatus.SHUTDOWN) { // 현재 객체가 종료 상태인지 확인
							// 제거
							remove(u);
							continue;
						}
						else {
							try{
								switch(getCurrentTriggerStatus()) {
								case NEW: // Branch가 NEW 상태면 실행중이면 실행중, 일시중지 상태인 모든 객체 정지
									if(urts == RoutineTriggerStatus.EXECUTE || urts == RoutineTriggerStatus.PAUSE) {
										u.requestStop();
									}
									break;
									
								case EXECUTE: // Branch가 EXECUTE 상태면 실행중이 아니던 모든 객체 실행 시작
									if(urts != RoutineTriggerStatus.EXECUTE) {
										u.requestExecute();
									}
									break;
									
								case PAUSE: // Branch가 PAUSE 상태면 실행대기, 실행중 상태인 모든 객체 일시중지
									if(urts == RoutineTriggerStatus.NEW || urts == RoutineTriggerStatus.EXECUTE) {
										u.requestPause();
									}
									break;
									
								case STOP: // Branch가 STOP 상태면 정지 상태가 아닌 모든 객체 정지
									if(urts != RoutineTriggerStatus.STOP) {
										u.requestStop();
									}
									break;
									
								case SHUTDOWN: // Branch가 SHUTDOWN 상태면 모든 객체 종료
									u.requestShutdown();
									break;
								}
							}
							catch (AccessControlException e) {
								
							}
							catch (Exception e) {
								log(Level.WARNING, "예외가 발생했습니다. "+e.getMessage()+" ("+e.getClass()+")");
							}
						}
					}
				}
			}
			
		}
		catch (Exception e) {
			log(Level.WARNING, "예외가 발생했습니다. "+e.getMessage()+" ("+e.getClass()+")");
		}
		finally {
			getLock().unlock();
		}
		
	}
	
	/**
	 * list에 target을 등록하는 메소드 (isAllowShutdown이 true일 경우 branch와 target이 shutdown상태여도 등록을 진행한다.) 
	 * 등록 성공시 자동으로 target의 부모가 메소드를 호출한 객체로 바뀜
	 * 
	 * @param target 리스트에 등록할 객체
	 * @return 성공 시 true, 실패 시 false
	 * @throws NullPointerException target값이 null인 경우 에외 발생
	 * @throws AccessControlException target값이 이미 부모를 가지고 있는경우
	 * */
	public boolean add(UEFCell target) throws NullPointerException, AccessControlException{
		boolean result = false;
		listCouncurrentDanger = true;
		
		// 인자가 null이면 예외호출
		if(target == null) throw new NullPointerException("target is null");
		else if(target.getParent() != null) throw new AccessControlException(target+" already has "+target.getParent()+" as a parent.");
		
		
		// branch도 꺼지지 않고 타겟도 꺼져 있지 않아야 한다.
		if(getCurrentTriggerStatus() != RoutineTriggerStatus.SHUTDOWN 
				&& target.getCurrentTriggerStatus() != RoutineTriggerStatus.SHUTDOWN) {
			try {
				getLock().lock();
				
				// 리스트에 존재하지 않는 객체면 등록진행
				if(!list.contains(target)) {
					if(target.getParent() != null && target.getParent() instanceof UEFBranch && !(target.getParent() instanceof UEFManager)) {
						UEFBranch bran = (UEFBranch) target.getParent();
						
						if(bran.remove(target) == null) {
							throw new Exception("Failed to remove "+target+" from "+target.getParent()+".");
						}
					}
					
					result = list.add(target);
					
					// 등록 성공 시 타겟의 부모객체를 현재 객체로 지정
					if(result) {
						target.setParent(this);
						getCommonCondition(0).signal();
					}
				}		
			}
			catch (Exception e) {
				e.printStackTrace();
			}
			finally {
				getLock().unlock();
			}
		}	
		
		return result;
	}
	
	@Override
	public void setFrame(long frame) {
		super.setFrame(frame);
		
		try {
			getLock().lock();
			
			Iterator<UEFCell> itr = list.iterator();
			
			// branch가 가진 모든 자식들이 프레임을 가질 수 있도록 업데이트 하는 과정
			while(itr.hasNext()) {
				UEFCell tempCell = itr.next();
				
				tempCell.setUseParentFrame(tempCell.isUseParentFrame());
			}
			
		}
		catch (Exception e) {
			e.printStackTrace();
		}
		finally {
			getLock().unlock();
		}
	}
	
	/**
	 * 시스템 종료시 이 객체의 스레드가 완전히 종료되는 것을 기다리게 해주는 메소드<br>
	 * (UEFBranch에서 메소드 사용시 자식개체들의 종료까지 보장)
	 * */
	@Override
	public void setWaitForEnd(boolean waitForEnd) {
		super.setWaitForEnd(waitForEnd);
		
		try {
			getLock().lock();
			
			Iterator<UEFCell> itr = list.iterator();
			
			// branch가 가진 모든 자식들이 프레임을 가질 수 있도록 업데이트 하는 과정
			while(itr.hasNext()) {
				UEFCell tempCell = itr.next();
				
				tempCell.setWaitForEnd(waitForEnd);
			}
		}
		catch (Exception e) {
			e.printStackTrace();
		}
		finally {
			getLock().unlock();
		}
	}
	
	/**
	 * list에 targetList를 등록하는 메소드 (isAllowShutdown이 true일 경우 branch와 targetList가 shutdown상태여도 등록을 진행한다.) 
	 * 
	 * @param targetList 리스트에 등록할 객체
	 * @throws NullPointerException targetList값이 null인 경우 에외 발생
	 * */
	public void addList(List<UEFCell> targetList) throws NullPointerException{
		boolean result = false;
		listCouncurrentDanger = true;
		
		// 인자가 null이면 예외호출
		if(targetList == null) throw new NullPointerException();
		
		// shutdown이여도 추가가 가능하거나 branch가 shutdown상태가 아니라면 추가를 진행한다.
		if(getCurrentTriggerStatus() != RoutineTriggerStatus.SHUTDOWN) {
			try {
				getLock().lock();
				
				// 리스트에 있는거 반복
				for(UEFCell cell: targetList) {
					// 리스트에 null이 들어있는경우 생략
					if(cell == null) continue;
					
					// 현재 리스트에 존재하지 않는 객체면서 shutdown이여도 추가가 가능하거나 타겟이 shutdown상태가 아니라면 추가를 진행한다.
					if(!list.contains(cell))
						if(cell.getCurrentTriggerStatus() != RoutineTriggerStatus.SHUTDOWN) {
							try {
								cell.setParent(this);
								result = list.add(cell);
								
								// 등록 실패 시 타겟의 부모객체를 현재 객체로 지정
								if(!result) cell.setParent(null);
							}
							catch (AccessControlException e) {
								log(Level.WARNING, "Failed to register "+cell.getName()+" to "+this);
							}
							
							
							
							
						}		
				}
				
			}
			catch (Exception e) {
				e.printStackTrace();
			}
			finally {
				getLock().unlock();
			}
		}
	}
	
	/**
	 * list에 target을 삭제하는 메소드 
	 * 
	 * @param target 리스트에 등록할 객체
	 * @return 성공 시 제거된 값을 리턴, 실패 시 null
	 * @throws NullPointerException target값이 null인 경우 에외 발생
	 * */
	public UEFCell remove(UEFCell target) throws NullPointerException{
		UEFCell result = null;
		listCouncurrentDanger = true;
		
		// 인자가 null이면 예외호출
		if(target == null) throw new NullPointerException();
		
		try {
			getLock().lock();
			
			// 반복자 생성
			Iterator<UEFCell> itr = list.iterator();
			
			// 반복시작
			while(itr.hasNext()) {
				// UEFCell 획득
				UEFCell cell = itr.next();
				
				// 인자 값과 동일한지 확인 후 [삭제-부모값초기화-종료]
				if(target.equals(cell)) {
					itr.remove();
					
					if(list.contains(cell)) {
						
					}
					else {
						cell.setParent(null);
						result = cell;
					}
					break;
				}
			}
			
		}
		catch (Exception e) {
			e.printStackTrace();
		}
		finally {
			getLock().unlock();
		}
		
		
		return result;
	}
	
	/**
	 * list에 targetList를 삭제하는 메소드
	 * 
	 * @param targetList 리스트에 등록할 객체
	 * @return 제거된 값을 리턴(제거된 값이 없으면 list size가 0)
	 * @throws NullPointerException targetList값이 null인 경우 에외 발생
	 * */
	public List<UEFCell> removeList(List<UEFCell> targetList) throws NullPointerException{
		List<UEFCell> result = new ArrayList<UEFCell>();
		
		// 인자가 null이면 예외호출
		if(targetList == null) throw new NullPointerException();
		listCouncurrentDanger = true;
		try {
			getLock().lock();
			
			// 반복자 생성
			Iterator<UEFCell> itr = list.iterator();
						
			// 반복시작
			while(itr.hasNext()) {
				UEFCell cell = itr.next();
				
				// 인자로 들어온 리스트에 삭제해야할 값이 존재하는지 확인 후 다음과 같이 처리[삭제-부모값초기화]
				if(targetList.contains(cell)) {
					itr.remove();
					cell.setParent(null);
					result.add(cell);
				}
			}
			
		}
		catch (Exception e) {
			e.printStackTrace();
		}
		finally {
			getLock().unlock();
		}
		
		return result;
	}
	
	/**
	 * branch에 존재하는 모든 자식객체들을 제거하는 메소드
	 * @return 
	 * @return 제거된 값을 리턴(제거된 값이 없으면 list size가 0)
	 * */
	public List<UEFCell> removeAll() {
		listCouncurrentDanger = true;
		List<UEFCell> result = new ArrayList<UEFCell>();
		
		try {
			getLock().lock();
			
			// 반복자 생성
			Iterator<UEFCell> itr = list.iterator();
									
			// [삭제-부모값초기화] <- 반복
			while(itr.hasNext()) {
				UEFCell cell = itr.next();
				
				itr.remove();
				
				cell.setParent(null);
				
				result.add(cell);
			}
			
		}
		catch (Exception e) {
			e.printStackTrace();
		}
		finally {
			getLock().unlock();
		}
		
		return result;
	}
	
	/**
	 * UEFBranch에 해당 객체가 들어있는지 확인하는 메소드
	 * @param target 리스트에 들어있는지 확인할 객체
	 * @return 들어있는 경우 true, 들어있지 않거나 예외가 발생하면 false
	 * */
	public boolean contains(UEFCell target) {
		boolean result = false;
		
		try {
			getLock().lock();
			
			result = list.contains(target);
		}
		catch (Exception e) {
			e.printStackTrace();
			result = false;
		}
		finally {
			getLock().unlock();
		}
		
		return result;
	}
	
	/**
	 * UEFBranch에 명책의 UEFCell이 들어있는지 리턴하는 메소드
	 * @return 들어있는 UEFCell의 갯수만큼 반환, 실패시 -1 반환
	 * */
	public int size() {
		int result = -1;
		
		try {
			getLock().lock();
			
			result = list.size();
		}
		catch (Exception e) {
			e.printStackTrace();
			result = -1;
		}
		finally {
			getLock().unlock();
		}
		
		return result;
	}
	
	@Override
	public void requestPause() {
		super.requestPause();
		
		try {
			if(getCurrentTriggerStatus() == RoutineTriggerStatus.EXECUTE || 
					getCurrentTriggerStatus() == RoutineTriggerStatus.NEW) {			
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
	public void requestExecute() {
		super.requestExecute();
		
		try {
			if(getCurrentTriggerStatus() == RoutineTriggerStatus.PAUSE ||
					getCurrentTriggerStatus() == RoutineTriggerStatus.STOP ||
					getCurrentTriggerStatus() == RoutineTriggerStatus.NEW) {
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
	public void requestStop() {
		super.requestStop();
		
		try {
			if(getCurrentTriggerStatus() == RoutineTriggerStatus.EXECUTE ||
					getCurrentTriggerStatus() == RoutineTriggerStatus.PAUSE ||
					getCurrentTriggerStatus() == RoutineTriggerStatus.NEW) {
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
	public void requestShutdown() {
		super.requestShutdown();
		
		try {
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
