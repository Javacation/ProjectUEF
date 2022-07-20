package org.UEF.core;

import java.security.AccessControlException;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Vector;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.logging.Level;

import org.UEF.enu.CoreOrderNumber;
import org.UEF.enu.RoutineTriggerStatus;
import org.UEF.others.DoubleQueue;
import org.UEF.others.Order;
import org.UEF.others.ResultWaitter;

/**
 * UEFManager는 UEFCell객체를 한번에 관리하기 위한 클래스로 싱글톤 형식으로 구현되어 있으며 3개의 스레드가 UEFManager를 관리한다.
 * <ul>
 * <li>
 * allCellListThread: 생성되는 모든 UEFCell객체의 존재여부를 담당하는 스레드로 사용자가 지정한 시간에 한번씩(기본 1초) shutdown상태에 들어간 UEFCell객체가 500ms가 지났는지 확인하고 제거한다.
 * </li>
 * <br>
 * <li>
 * orderInterpreterThread: UEFManager를 통해 관리할 수 있도록 설정을 담당하는 스레드로 관리할 UEFLeaf, UEFBranch의 추가, 삭제, 조작 및 UEFManager의 전체 설정을 조작하는 명령을 실행한다.
 * </li>
 * <br>
 * <li>
 * shutdownHook: 시스템 종료시 사용자가 설정한 스레드들이 안전하게 종료될 수 있도록 조작하는 스레드이다. (시스템이 종료되면 1, 2번 스레드는 종료되고 해당 스레드가 실행된다.)
 * </li>
 * </ul>
 * */
public final class UEFManager extends UEFCell {
	// 싱글톤 인스턴스
	private static UEFManager 
		instance = null;
	
	// Cell 전체를 관리하는 리스트
	protected final List<UEFCell> 
		allCellList = new Vector<UEFCell>();
	
	// 사용자가 조작하고자 하는 Cell을 등록하는 리스트
	protected final List<UEFBranch> 
		manageCellList = new Vector<UEFBranch>();
	
	/*
	 * Cell 전체 리스트를 관리하는 스레드
	 * 명령을 해석해 관리 리스트를 조작하는 스레드
	 * 시스템을 종료했을 때 처리할 스레드
	 * */
	protected Thread 
		allCellListThread = null,		
		orderInterpreterThread = null,	
		shutdownHook = null;			
	
	// 사용자가 처리하고자 할 명령을 담는 Queue로 orderInterpreter에서 사용한다.
	protected DoubleQueue<Order> 
		doubleQueue = new DoubleQueue<Order>();
	
	/*
	 * 스레드 작동을 시작했는지 나타내는 값
	 * 명령 해석 스레드와 리스트 관리 스레드가 끝났는지 저장하는 값
	 * */
	boolean 
		isLaunched = false,
		isOver = false,
		shutdownHookIsStarted = false;
	
	/*
	 * exit()을 여러번 호출하지 못하게하는 변수
	 * UEF 라이브러리를 종료시키기 위해 사용되는 값
	 * */
	private boolean 
		doNotCallThis4exit = false,
		isEnd = false;
	
	
	
	private UEFManager(String name) {
		super(name, false, false);
		
		log(Level.INFO, "UEF 라이브러리를 시작합니다.");
		/*
		 * 전체 리스트 관리 Runnable
		 * */
		Runnable allCellListThreadRunnable = new Runnable() {
			@Override
			public void run() {
				try {
					log(Level.INFO, "AllCellListThread 시작");
					
					while(isOver == false) {
						Thread.sleep(500);
						
						Iterator<UEFCell> itr = allCellList.iterator();
						
						while(itr.hasNext()) {
							UEFCell cell = itr.next();
							
							if(cell.getCurrentTriggerStatus() == RoutineTriggerStatus.SHUTDOWN) {
								itr.remove();
							}
						}
					}
				}
				catch (InterruptedException e1) {
					// 정상적인 종료인 경우
				}
				catch (Exception e2) {
					log(Level.SEVERE, e2.getMessage());
				}
				finally {
					log(Level.INFO, "AllCellListThread 종료");
				}
			}
		};
		
		/*
		 * 명령 해석 Runnable
		 * */
		Runnable orderInterpreterThreadRunnable = new Runnable() {
			@Override
			public void run() {
				Queue<Order> tempQueue = null;
				Iterator<Order> itr = null;
				Iterator<UEFBranch> manageItr = null;
				try {
					log(Level.INFO, "OrderInterpreterThread 시작");
					
					UEFBranch defaultBranch = new UEFBranch("default", true, true) {};
					defaultBranch.setWaitForEnd(true);
					defaultBranch.setParent(UEFManager.getInstance());
					
					manageCellList.add(defaultBranch);
					
					while(isOver == false) {
						do {
							// 큐가 계속 비어있는 경우 100ms 대기
							Thread.sleep(100);
						} while(doubleQueue.isEmpty());
						
						// 큐 빌려오기
						tempQueue = doubleQueue.borrowQueue();
						
						// 반복자 생성
						itr = tempQueue.iterator();
						
						// 큐관련 작업을 처리하기 전 관리리스트를 정리
						manageItr = manageCellList.iterator();
						
						while (manageItr.hasNext()) {
							UEFBranch tempBranch = manageItr.next();
							
							if(tempBranch.getCurrentTriggerStatus() == RoutineTriggerStatus.SHUTDOWN) {
								manageItr.remove();
							}
								
						}
						
						// 큐가 비어있는지 확인
						while(itr.hasNext()) {
							// 큐에서 꺼내오기
							Order tempOrder = itr.next();
							
							// System.out.println(tempOrder);
							
							// null 일 경우 생략 
							if(tempOrder == null) continue;
							
							try {
								switch (tempOrder.getOrderNumber()) {
									case 100: // REGIST_LO - UEFLeaf
										RegistLeaf(tempOrder); break;
											
									case 101: // REGIST_BO - UEFBranch 
									case 102: // REGIST_BN - UEFBranchName
										RegistBranch(tempOrder); break;
									
									case 103: // REGIST_LOBO - UEFLeaf, UEFBranch 
										RegistLeafByBranchObject(tempOrder); break;
										
									case 104: // REGIST_LOBN - UEFLeaf, UEFBranchName
										RegistLeafByBranchName(tempOrder); break;
								
									
									case 200: // REMOVE_LO - UEFLeaf
										RemoveLeaf(tempOrder); break;
									
									case 201: // REMOVE_BO - UEFBranch 
									case 202: // REMOVE_BN - UEFBranchName
										RemoveBranch(tempOrder); break;
									 
									case 203: // REMOVE_LOBO - UEFLeaf, UEFBranch
										RemoveLeafByBranchObject(tempOrder); break;
									case 204: // REMOVE_LOBN - UEFLeaf, UEFBranchName
										RemoveLeafByBranchName(tempOrder); break;
										
									case 300: // SET_FRAME - frame
										SetFrame(tempOrder); break;
										
									case 301: // SET_STREAM - OutputStream - deprecated
										// SetStream(tempOrder);
										break;
										
									case 302: // EXIT_UEFMANAGER
										ExitUEFManager(tempOrder); break;
										
									case 400: // REQUEST_EXECUTE - UEFBranchNamePattern
									case 401: // REQUEST_PAUSE - UEFBranchNamePattern
									case 402: // REQUEST_STOP - UEFBranchNamePattern
									case 403: // REQUEST_SHUTDOWN - UEFBranchNamePattern
										RequestTrigger(tempOrder); break;

									
									default:
										break;
								}
							}
							catch (Exception e) {
								log(Level.WARNING, tempOrder+" 실행에 실패했습니다. ("+e.getMessage()+")");
							}
							finally {
								itr.remove();
							}
						}
					}
				}
				catch (InterruptedException e1) {
					// 정상적인 종료인 경우
				}
				catch (Exception e2) {
					log(Level.SEVERE, e2.getMessage());
				}
				finally {
					log(Level.INFO, "OrderInterpreterThread 종료");
				}
			}
		};
		
		/*
		 * 시스템 종료 Runnable
		 * */
		Runnable shutdownHookRunnable = new Runnable() {
			@Override
			public void run() {
				if(shutdownHookIsStarted == true) {
					return;
				}
				else {
					shutdownHookIsStarted = true;
				}
				
				Iterator<UEFCell> itr = null;
				// 끝내기 선언
				isOver = true;
				
				// 스레드 종료선언
				allCellListThread.interrupt();
				// 스레드 종료선언
				orderInterpreterThread.interrupt();
				
				
				try {
					log(Level.INFO, "ShutdownHook 시작");
					
					
					allCellListThread.join();
					orderInterpreterThread.join();
					
					
					
					// 반복자 생성
					itr = allCellList.iterator();
					
					log(Level.INFO,"------------------------------Shutdown 시작------------------------------");
					
					// 종료요청이 가지 않은 모든 Cell에 requestShutdown요청
					if(itr != null) {
						while(itr.hasNext()) {
							UEFCell cell = itr.next();
							try {
								cell.requestShutdown();
								
								log(Level.INFO, cell+" Shutdown 성공");
							}
							catch (AccessControlException e1) {
								// 이미 셧다운 됬음
								log(Level.INFO, cell+" Shutdown 성공(already)");
							}
							catch (Exception e2) {
								log(Level.WARNING, cell+" Shutdown 실패: "+e2.getMessage());
							}
						}
					}
					
					// 반복자 생성
					itr = allCellList.iterator();
					
					
					log(Level.INFO,"------------------------------join 시작------------------------------");
					if(itr != null) {
						// 반드시 종료되어야하는 스레드 join으로 기다리기
						while(itr.hasNext()) {
							UEFCell cell = itr.next();
							
							if(cell.isWaitForEnd()) {
								try {
									cell.join();
									log(Level.INFO, cell+" join 성공");
								}
								catch (Exception e) {
									log(Level.WARNING, cell+" join 실패: "+e.getMessage());
								}
							}
						}
					}
					
					
					log(Level.INFO, "ShutdownHook 종료");
					log(Level.INFO, "------------------------------UEF라이브러리 종료 성공------------------------------");
				}
				catch (Exception e2) {
					log(Level.SEVERE, "시스템 종료 실패: "+e2.getMessage());
				}
				finally {
					
				}
			}
		};
		
		allCellListThread = new Thread(allCellListThreadRunnable);
		orderInterpreterThread = new Thread(orderInterpreterThreadRunnable);
		shutdownHook = new Thread(shutdownHookRunnable);
		
		allCellListThread.setName("AllCellListThread");
		orderInterpreterThread.setName("OrderInterpreterThread");
		shutdownHook.setName("ShutdownHook");
		
		Runtime.getRuntime().addShutdownHook(shutdownHook);
	}
	
	/**
	 * 전체 Cell 리스트에 new된 객체를 넣는 메소드
	 * @param cell 전체 리스트에 넣을 값
	 * */
	static final void addCell(UEFCell cell) {
		if(!getInstance().allCellList.contains(cell) && getInstance().isEnd() == false) {
			getInstance().allCellList.add(cell);
		}
	}
	
	/**
	 * UEFManager를 사용을 호출하는 메소드로 전체 리스트 관리 스레드, 명령 해석 스레드를 실행시킨다.
	 * */
	public final synchronized void launch() {
		if(isLaunched == false) {
			isLaunched = true;
			
			allCellListThread.start();
			orderInterpreterThread.start();
		}
	}
	
	/**
	 * UEFManager를 리턴하는 메소드 (UEFManager는 단 한번만 생성된다.)
	 * @return UEFManager
	 * */
	public static UEFManager getInstance() {
		if(instance == null) instance = new UEFManager("UEFManager");
		
		return instance.isEnd? null: instance;
	}
	
	@Override
	public void log(Level logLevel, String contents) {	
		super.log(logLevel, contents);
	}

	
	/**
	 * UEFManager에 {@link Order}를 전달하는 메소드로 {@link CoreOrderNumber}에 따라 다른 인자값을 넣어야한다.
	 * <ul>
	 * <b>{@link CoreOrderNumber}에 따른 인자값 (값에 따른 실행내용은 {@link CoreOrderNumber}참조)</b>
	 * <li>{@link CoreOrderNumber REGIST_LO} - UEFLeaf</li>
	 * <li>{@link CoreOrderNumber REGIST_BO} - UEFBranch</li>
	 * <li>{@link CoreOrderNumber REGIST_BN} - String</li>
	 * <li>{@link CoreOrderNumber REGIST_LOBO} - UEFLeaf, UEFBranch</li>
	 * <li>{@link CoreOrderNumber REGIST_LOBN} - UEFLeaf, String</li>
	 * <li>{@link CoreOrderNumber REMOVE_LO} - UEFLeaf</li>
	 * <li>{@link CoreOrderNumber REMOVE_BO} - UEFBranch</li>
	 * <li>{@link CoreOrderNumber REMOVE_BN} - String</li>
	 * <li>{@link CoreOrderNumber REMOVE_LOBO} - UEFLeaf, UEFBranch</li>
	 * <li>{@link CoreOrderNumber REMOVE_LOBN} - UEFLeaf, String</li>
	 * <li>{@link CoreOrderNumber SET_FRAME}} - Long</li>
	 * <li>(사용중지){@link CoreOrderNumber SET_STREAM}} - OutputStream</li>
	 * <li>{@link CoreOrderNumber REQUEST_EXECUTE} - String</li>
	 * <li>{@link CoreOrderNumber REQUEST_PAUSE} - String</li>
	 * <li>{@link CoreOrderNumber REQUEST_STOP} - String</li>
	 * <li>{@link CoreOrderNumber REQUEST_SHUTDOWN} - String</li>
	 * </ul>
	 * @param con 실행하고자 하는 명령
	 * @param args 명령에 사용될 인자값
	 * @exception NullPointerException
	 * */
	public void sendOrder(CoreOrderNumber con, Object ... args) {
		Order tempOrder = new Order(con.getValue());
		
		for(int i = 0; i < args.length; i++) {
			tempOrder.put("arg"+i, args[i]);
		}
		
		doubleQueue.offer(tempOrder);
	}
	
	/**
	 * defaultBranch에 leaf를 등록하는 메소드 (이미 defaultBranch에 leaf가 등록되어있으면 실패함)
	 * @param leaf UEFManager의 defaultBranch에 등록할 UEFLeaf (성공시 importatnt가 됨)
	 * @exception NullPointerException leaf가 null인 경우
	 * */
	public void regist(UEFLeaf leaf) {
		if(leaf == null) throw new NullPointerException("leaf is null");
		
		sendOrder(CoreOrderNumber.REGIST_LO, leaf);
	}
	
	/**
	 * branch를 UEFManager에 등록하는 메소드 (이미 branch가 UEFManger에 등록되어있거나 이름이 동일한 UEFBranch가 존재한다면 실패함)
	 * @param branch UEFManager에 등록할 UEFBranch (성공시 importatnt가 됨)
	 * @exception NullPointerException branch가 null인 경우
	 * */
	public void regist(UEFBranch branch) {
		if(branch == null) throw new NullPointerException("branch is null");
		
		sendOrder(CoreOrderNumber.REGIST_BO, branch);
	}
	
	/**
	 * name을 가진 Branch를 UEFManager에 등록하는 메소드 (이름이 겹치는 UEFBranch가 이미 등록되어있으면 실패함) 
	 * @param branchName UEFManager에 등록할 UEFBranch의 이름 (성공시 importatnt가 됨)
	 * @exception NullPointerException branchName이 null인 경우
	 * */
	public void regist(String branchName) {
		if(branchName == null) throw new NullPointerException("name is null");
		
		sendOrder(CoreOrderNumber.REGIST_BN, branchName);
	}
	
	/**
	 * branch에 leaf를 등록하는 메소드로 branch가 UEFManager에 포함되어 있지않으면 branch를 UEFManager에 등록한다. (이름이 겹치는 UEFBranch가 이미 등록되어있으면 실패함) 
	 * @param leaf branch에 등록할 UEFLeaf (성공시 importatnt가 됨)
	 * @param branch leaf를 등록시킬 UEFBranch로 UEFManager에 등록되어 있지않으면 등록시킴 (성공시 importatnt가 됨)
	 * @exception NullPointerException leaf가 null이거나 branch가 null인 경우
	 * */
	public void regist(UEFLeaf leaf, UEFBranch branch) {
		if(leaf == null) throw new NullPointerException("leaf is null");
		else if(branch == null) throw new NullPointerException("branch is null");
		
		sendOrder(CoreOrderNumber.REGIST_LOBO, leaf, branch);
	}
	
	/**
	 * branchName을 가진 Branch를 등록하고 Branch에 leaf를 등록하는 메소드로 UEFManager에 포함되어 있지않으면 branchName을 가진UEFBranch를 UEFManager에 등록한다. (이름이 겹치는 UEFBranch가 이미 등록되어있으면 실패함) 
	 * @param leaf branchName을 가진 UEFBranch에 등록할 UEFLeaf (성공시 importatnt가 됨)
	 * @param branchName leaf를 등록시킬 UEFBranch의 이름으로 UEFManager에 등록되어 있지않으면 등록시킴 (성공시 importatnt가 됨)
	 * @exception NullPointerException leaf가 null이거나 branchName이 null인 경우
	 * */
	public void regist(UEFLeaf leaf, String branchName) {
		if(leaf == null) throw new NullPointerException("leaf is null");
		else if(branchName == null) throw new NullPointerException("branchName is null");
		
		sendOrder(CoreOrderNumber.REGIST_LOBN, leaf, branchName);
	}
	
	/**
	 * defaultBranch에 leaf를 제거하는 메소드 default에서 제거하지 못한경우 등록된 모든 branch에서 leaf를 찾아서 제거한다. (어떤 branch에도 leaf가 등록되어 있지 않으면 실패함)<br>
	 * 성공시 leaf는 importatnt가 해제되고 ResultWaitter에서 값을 받을 수 있음 실패할 경우 {@link ResultWaitter}에서 null을 반환함
	 * @param leaf UEFManager에서 제거하고자 하는 UEFLeaf 
	 * @return 제거된 UEFLeaf를 받을 수 있도록 대기시켜주는 {@link ResultWaitter}
	 * @exception NullPointerException leaf가 null인 경우
	 * */
	public ResultWaitter<UEFLeaf> remove(UEFLeaf leaf){
		ResultWaitter<UEFLeaf> result = new ResultWaitter<UEFLeaf>();
		
		if(leaf == null) throw new NullPointerException("leaf is null");
		
		sendOrder(CoreOrderNumber.REMOVE_LO, result, leaf);
		
		return result;
	}
	
	/**
	 * UEFManager에 branch를 제거하는 메소드 branch가 UEFManager에 등록되어 있지 않은경우 실패한다.<br>
	 * 성공시 branch는 importatnt가 해제되고 ResultWaitter에서 값을 받을 수 있음 실패할 경우 {@link ResultWaitter}에서 null을 반환함
	 * @param branch UEFManager에서 제거하고자 하는 UEFBranch
	 * @return 제거된 UEFBranch를 받을 수 있도록 대기시켜주는 {@link ResultWaitter}
	 * @exception NullPointerException branch가 null인 경우
	 * */
	public ResultWaitter<UEFBranch> remove(UEFBranch branch) {
		ResultWaitter<UEFBranch> result = new ResultWaitter<UEFBranch>();
		
		if(branch == null) throw new NullPointerException("branch is null");
		
		sendOrder(CoreOrderNumber.REMOVE_BO, result, branch);
		
		return result;
	}
	
	/**
	 * UEFManager에 UEFBranch를 branchName으로 찾아 제거하는 메소드 UEFManager에 등록되어 있지 않은경우 실패한다.<br>
	 * 성공시 UEFBranch는 importatnt가 해제되고 ResultWaitter에서 값을 받을 수 있음 실패할 경우 {@link ResultWaitter}에서 null을 반환함
	 * @param branchName UEFManager에서 제거하고자 하는 UEFBranch의 이름
	 * @return 제거된 UEFBranch를 받을 수 있도록 대기시켜주는 {@link ResultWaitter}
	 * @exception NullPointerException branchName이 null인 경우
	 * */
	public ResultWaitter<UEFBranch> remove(String branchName) {
		ResultWaitter<UEFBranch> result = new ResultWaitter<UEFBranch>();
		
		if(branchName == null) throw new NullPointerException("branchName is null");
		
		sendOrder(CoreOrderNumber.REMOVE_BO, result, branchName);
		
		return result;
	}
	
	/**
	 * UEFManager의 특정 branch에 leaf를 제거하는 메소드 UEFManager에 branch가 등록되어 있지 않거나 branch에 leaf가 등록되어있지 않은경우 실패한다.<br>
	 * 성공시 leaf는 importatnt가 해제되고 ResultWaitter에서 값을 받을 수 있음 실패할 경우 {@link ResultWaitter}에서 null을 반환함
	 * @param leaf UEFManager에서 제거하고자 하는 UEFUEFLeaf
	 * @param branch UEFManager에서 제거하고자 하는 leaf를 가지고 있는 UEFBranch
	 * @return 제거된 UEFLeaf를 받을 수 있도록 대기시켜주는 {@link ResultWaitter}
	 * @exception NullPointerException leaf가 null이거나, branch가 null인 경우
	 * */
	public ResultWaitter<UEFLeaf> remove(UEFLeaf leaf, UEFBranch branch) {
		ResultWaitter<UEFLeaf> result = new ResultWaitter<UEFLeaf>();
		
		if(leaf == null) throw new NullPointerException("leaf is null");
		else if(branch == null) throw new NullPointerException("branch is null");
		
		sendOrder(CoreOrderNumber.REMOVE_LOBO, result, leaf, branch);
		
		return result;
	}
	
	/**
	 * UEFManager에서 특정이름을 가진 UEFBranch에서 leaf를 제거하는 메소드 UEFManager에서 해당이름을 가진 UEFBranch가 등록되어 있지 않거나 UEFBranch에 leaf가 등록되어있지 않은경우 실패한다.<br>
	 * 성공시 leaf는 importatnt가 해제되고 ResultWaitter에서 값을 받을 수 있음 실패할 경우 {@link ResultWaitter}에서 null을 반환함
	 * @param leaf UEFManager에서 제거하고자 하는 UEFUEFLeaf
	 * @param branchName UEFManager에서 제거하고자 하는 leaf를 가지고 있는 UEFBranch의 이름
	 * @return 제거된 UEFLeaf를 받을 수 있도록 대기시켜주는 {@link ResultWaitter}
	 * @exception NullPointerException leaf가 null이거나, branchName이 null인 경우
	 * */
	public ResultWaitter<UEFLeaf> remove(UEFLeaf leaf, String branchName) {
		ResultWaitter<UEFLeaf> result = new ResultWaitter<UEFLeaf>();
		
		if(leaf == null) throw new NullPointerException("leaf is null");
		else if(branchName == null) throw new NullPointerException("branchName is null");
		
		sendOrder(CoreOrderNumber.REMOVE_LOBN, result, leaf, branchName);
		
		return result;
	}
	
	/**
	 * UEFManager의 프레임을 지정하는 메소드로 {@link UEFManager sendOrder(CoreOrderNumber.SET_FRAME, frame)}를 통해 업데이트 되기때문에 호출 즉시 반영되지 않는다.<br>
	 * <ul>
	 * <li>MIN_FRAME = 1, MAX_FRAME = 1e+9</li>
	 * <li></li>
	 * <li><b>1  frame </b> : this.frame = MIN_FRAME</li>
	 * <li><b>1  frame  1e+9</b> : this.frame = frame</li>
	 * <li><b>frame  1e+9</b> : this.frame = MAX_FRAME</li>
	 * </ul>
	 * @param frame 지정할 프레임 값
	 * */
	@Override
	public void setFrame(long frame){
		
		if(frame < MIN_FRAME) {
			frame = MIN_FRAME;
		}
		else if(frame > MAX_FRAME) {
			frame = MAX_FRAME;
		}
		
		sendOrder(CoreOrderNumber.SET_FRAME, frame);
	}
	
	/**
	 * UEFManager의 관리하에있는 UEFBranch들중 패턴이 겹치는 객체를 실행하는 메소드<br>
	 * @param rts 실행할 Trigger
	 * @param pattern 실행시킬 UEFBranch 이름패턴("^(.)*$"을 삽입하면 전체를 대상으로 진행)
	 * @throws IllegalAccessException RoutineTriggerStatus.NEW를 삽입한 경우 예외발생
	 * 
	 * */
	public void requestTrigger(RoutineTriggerStatus rts, String pattern) throws IllegalAccessException {
		if(pattern == null) throw new NullPointerException("pattern is null");
		else if(rts == null) throw new NullPointerException("rts is null");
		else if(rts == RoutineTriggerStatus.NEW) throw new IllegalAccessException("RoutineTriggerStatus.NEW is an invalid request.");
		
		switch(rts) {
		case EXECUTE:
			sendOrder(CoreOrderNumber.REQUEST_EXECUTE, pattern);
			break;
			
		case PAUSE:
			sendOrder(CoreOrderNumber.REQUEST_PAUSE, pattern);
			break;
			
		case STOP:
			sendOrder(CoreOrderNumber.REQUEST_STOP, pattern);
			break;
			
		case SHUTDOWN:
			sendOrder(CoreOrderNumber.REQUEST_SHUTDOWN, pattern);
			break;
			
		default:
			break;
		}
	}
	
	/**
	 * 현재 UEFManager에 등록되어 있는 모든 Brnach를 Execute상태로 만든다.
	 * */
	@Override
	public void requestExecute() {
		try {
			requestTrigger(RoutineTriggerStatus.EXECUTE, "^(.)*$");
		} catch (IllegalAccessException e) {
			e.printStackTrace();
		}
	}
	
	/**
	 * 현재 UEFManager에 등록되어 있는 모든 Brnach를 Pause상태로 만든다.
	 * */
	@Override
	public void requestPause() {
		try {
			requestTrigger(RoutineTriggerStatus.PAUSE, "^(.)*$");
		} catch (IllegalAccessException e) {
			e.printStackTrace();
		}
	}
	
	/**
	 * 현재 UEFManager에 등록되어 있는 모든 Brnach를 Stop상태로 만든다.
	 * */
	@Override
	public void requestStop() {
		try {
			requestTrigger(RoutineTriggerStatus.STOP, "^(.)*$");
		} catch (IllegalAccessException e) {
			e.printStackTrace();
		}
	}
	
	/**
	 * 현재 UEFManager에 등록되어 있는 모든 Brnach를 Shutdown상태로 만든다.
	 * */
	@Override
	public void requestShutdown() {
		try {
			requestTrigger(RoutineTriggerStatus.SHUTDOWN, "^(.)*$");
		} catch (IllegalAccessException e) {
			e.printStackTrace();
		}
	}
	
	/**
	 * UEF 라이브러리가 종료됬는지를 반환하는 메소드
	 * @return 종료된 경우 true를 반환함
	 * */
	public boolean isEnd() {
		return isEnd;
	}
	
	/**
	 * UEF 라이브러리가 시작됬는지를 반환하는 메소드
	 * @return 시작된 경우 true를 반환함
	 * */
	public boolean isLaunched() {
		return isLaunched;
	}
	
	/*
	 *  ====================================================================================================================================
	 *  명령 해석 스레드에 사용될 메소드
	 *  ====================================================================================================================================
	 */
	
	/**
	 * 명령 해석 스레드에서 공통으로 사용되는 메소드로 ManageCellList에 등록시 thisIsVeryImportant()를 실행한다.
	 * @param target thisIsVeryImportant()을 실행할 UEFCell
	 * @param isThisImportant true일 경우 target의 thisIsVeryImportant()를 실행한다.
	 * */
	private void commonRegist(UEFCell target, boolean waitForEnd) {
		target.setWaitForEnd(waitForEnd);
	}
	
	/**
	 * 명령 해석 스레드에서 사용되는 메소드로 Leaf를 "default" 또는 Leaf의 이름과 같은 Branch에 등록한다.<br>
	 * 인자: arg0 - UEFLeaf<br>
	 * 명령번호: 100
	 * @param order 처리할 명령
	 * @throws Exception 
	 * */
	private void RegistLeaf(Order order) throws Exception {
		Map<String, Object> orderMap = order.getMap();
		Iterator<UEFBranch> itr = manageCellList.iterator();
		UEFLeaf leaf = (UEFLeaf) orderMap.get("arg0");
		
		/*
		 * leaf가 null일 경우 예외발생
		 * leaf가 parent를 가지는 경우 예외발생(이미 다른곳에 소속됨)
		 * */
		if(leaf == null) throw new Exception("leaf(arg0) is null");
		else if(leaf.getParent() != null) {
			throw new Exception(leaf+"(arg0) already has "+leaf.getParent()+" as a parent.");
		}
		
		try {
			while(itr.hasNext()) { // UEFManager에서 관리하는 Branch중 이름이 "default"인 곳에 등록한다.
				UEFBranch tempBranch = itr.next();
				
				if(tempBranch == null) continue;
				else if(tempBranch.getName().equals("default")) {
						
					if(!tempBranch.contains(leaf)) { // "default"에 leaf가 존재하는지 확인
						if(tempBranch.add(leaf)) { // "default"에 leaf 삽입 실패시 예외발생
							commonRegist(leaf, true);
							
							// 조건문에서 add를 실행한 순간 부모를 따로 지정할 필요가 없음
							// leaf.setParent(tempBranch);
						}
						else throw new Exception("Failed to register "+leaf.getName()+" to UEFManager.default");
						
						return;
					}
					
				}
			}
			
			throw new Exception("Couldn't find 'default' branch");
		}
		catch (Exception e) {
			throw e;
		}
		
	}
	
	/**
	 * 명령 해석 스레드에서 사용되는 메소드로 Branch를 등록한다. (이미 존재하거나 같은 이름의 branch가 존재한다면 등록 불가능)<br>
	 * 인자: arg0 - UEFBranch<br>
	 * 명령번호: 101 or 102
	 * @param order 처리할 명령
	 * @throws Exception 
	 * */
	private void RegistBranch(Order order) throws Exception {
		Map<String, Object> orderMap = order.getMap();
		Iterator<UEFBranch> itr = manageCellList.iterator();
		Object arg0 = orderMap.get("arg0");
		String name = null;
		UEFBranch branch = null;
		String findName = null;
		
		if(arg0 == null) { // 이름 또는 branch가 null이면 예외발생
			throw new Exception("name or branch(arg0) is null");
		}
		else if(arg0 instanceof String) { // 이름인 경우
			name = (String) arg0;
			findName = name;
		}
		else if(arg0 instanceof UEFBranch) { // branch인 경우
			branch = (UEFBranch) arg0;
			
			// 리스트에 branch가 존재한다면 예외발생
			if(manageCellList.contains(branch)) 
				throw new Exception(branch.getName()+" is already registered");
			
			// branch가 어딘가에 소속되어 있으면 예외발생
			if(branch.getParent() != null)
				throw new Exception(branch+"(arg0) already has "+branch.getParent()+" as a parent.");
			
			findName = branch.getName();
		}
		
		if(findName == null) { // 이름이 없거나 branch의 이름이 없으면 예외발생
			throw new Exception("name or branch.getName() is null");
		}
		
		
		try {
			while(itr.hasNext()) {
				UEFBranch tempBranch = itr.next();
				
				// 이름이 같다면  예외호출
				if(tempBranch.getName().equals(findName)) 
					throw new Exception("branch name is already used("+branch.getName()+")");
				
			}
			
			
			if(branch != null) { // 인자값이 UEFBranch인 경우
				if(manageCellList.add(branch)) { // manageCellList에 branch추가 
					branch.setParent(this);
					commonRegist(branch, true);
				}
				else {
					throw new Exception("Failed to register "+branch+" to UEFManager");
				}
			}
			else if(name != null){ // 인자값이 String name인 경우
				// findName으로 branch추가 
				UEFBranch tempBranch = new UEFBranch(findName, true, true) {};
				
				if(manageCellList.add(tempBranch)) {
					commonRegist(tempBranch, true);
					tempBranch.setParent(this);
				}
				else {
					throw new Exception("Failed to register "+tempBranch+" to UEFManager");
				}
			}
		}
		catch (Exception e) {
			throw e;
		}
		
		
	}
	
	/**
	 * 명령 해석 스레드에서 사용되는 메소드로 관리하에있는 Branch에 Leaf를 등록한다. (인자로 들어온  branch가 존재하지 않으면 Leaf 등록 불가능)<br>
	 * 인자: arg0 - UEFLeaf, arg1 - UEFBranch<br>
	 * 명령번호: 103
	 * @param order 처리할 명령
	 * @throws Exception 
	 * */
	private void RegistLeafByBranchObject(Order order) throws Exception {
		Map<String, Object> orderMap = order.getMap();
		Iterator<UEFBranch> itr = null;
		UEFLeaf leaf = (UEFLeaf) orderMap.get("arg0");
		UEFBranch branch = (UEFBranch) orderMap.get("arg1");
		
		if(leaf == null) { // leaf가 null이면 예외발생
			throw new Exception("leaf(arg0) is null");
		}
		else if(branch == null) { // branch가 null이면 예외발생
			throw new Exception("branch(arg1) is null");
		}
		else if(leaf.getCurrentTriggerStatus() == RoutineTriggerStatus.SHUTDOWN) { // leaf가 SHUTDOWN 상태면 예외발생
			throw new Exception(leaf+"(arg0) is already shutdown");
		}
		else if(branch.getCurrentTriggerStatus() == RoutineTriggerStatus.SHUTDOWN) { // branch가 SHUTDOWN 상태면 예외발생
			throw new Exception(branch+"(arg1) is already shutdown");
		}
		else if(leaf.getParent() != null) {
			throw new Exception(leaf+"(arg0) already has "+leaf.getParent()+" as a parent.");
		}
		
		try {
			if(manageCellList.contains(branch)) { // branch가 UEFManager에 등록되어있는지 확인
				if(branch.add(leaf)) { // leaf -> branch
					commonRegist(leaf, true);
					
				}
				else { // branch에 leaf등록을 실패한 경우 발생
					throw new Exception("Failed to register "+leaf+"(arg0) to "+branch+"(arg1)");
				}
			}
			else { // 포함되어있지 않은경우 해당 branch와 이름이 겹치는 객체가 존재하는지 확인 후 삽입
				itr = manageCellList.iterator();
				
				while (itr.hasNext()) {
					UEFBranch tempBranch = itr.next();
					
					if(tempBranch.getName().equals(branch.getName())) { // 겹치는 이름이 존재하므로 예외발생
						throw new Exception("branch name is already used("+branch.getName()+")");
					}
					
				}
				
				if(manageCellList.add(branch)) { // branch를 UEFManager에 등록하고 여부 확인
					commonRegist(branch, true);
					branch.setParent(this);
					
					if(branch.add(leaf)) { // leaf -> branch
						commonRegist(leaf, true);
					}
					else { // branch에 leaf등록을 실패한 경우 발생
						throw new Exception("Failed to register "+leaf+"(arg0) to "+branch+"(arg1)");
					}
				}
				else { // 등록에 실패한 경우 예외발생
					throw new Exception("Failed to register "+branch+"(arg1) to UEFManager");
				}
			}
		}
		catch (Exception e) {
			throw e;
		}
	}
	
	/**
	 * 명령 해석 스레드에서 사용되는 메소드로 관리하에있는 Branch에 Leaf를 등록한다. (인자로 들어온 이름이 존재하지 않으면 Leaf 등록 불가능)<br>
	 * 인자: arg0 - UEFLeaf, arg1 - String<br>
	 * 명령번호: 104
	 * @param order 처리할 명령
	 * @throws Exception 
	 * */
	private void RegistLeafByBranchName(Order order) throws Exception {
		Map<String, Object> orderMap = order.getMap();
		Iterator<UEFBranch> itr = manageCellList.iterator();
		UEFLeaf leaf = (UEFLeaf) orderMap.get("arg0");
		String branchName = (String) orderMap.get("arg1");
		
		if(leaf == null) { // leaf가 null이면 예외발생
			throw new Exception("leaf(arg0) is null");
		}
		else if(branchName == null) { // 찾고자 하는 branch이름이 null이면 예외발생
			throw new Exception("branchName(arg1) is null");
		}
		else if(leaf.getParent() != null) {
			throw new Exception(leaf+"(arg0) already has "+leaf.getParent()+" as a parent.");
		}
		
		try {
			while(itr.hasNext()) { // name과 같은 이름을 가진 branch가 있는지 확인
				UEFBranch tempBranch = itr.next();
				
				// 이름이 같다면 해당 branch에 Leaf추가
				if(tempBranch.getName().equals(branchName)) {
					if(tempBranch.add(leaf)) {
						commonRegist(leaf, true);
						
						return;
					}
					else {
						// 넣고자하는 branch에 삽입을 실패하면 예외발생
						throw new Exception("Failed to register "+leaf+"(arg0) to "+tempBranch.getName());
					}
				}
			}
			
			// 해당 이름으로 branch가 존재하지 않는걸 확인
			UEFBranch tempBranch = new UEFBranch(branchName, true, true) {};
			
			if(manageCellList.add(tempBranch)) { // tempBranch -> UEFManager
				commonRegist(tempBranch, true);
				tempBranch.setParent(this);
				
				if(tempBranch.add(leaf)) { // leaf -> tempBranch
					commonRegist(leaf, true);
					
				}
				else {
					// leaf가 tempBranch의 관리에 들어가지 못하면 예외발생
					throw new Exception("Failed to register "+leaf+"(arg0) to "+tempBranch.getPath());
				}
			}
			else {
				// tempBranch가 UEFManager의 관리에 들어가지 못하면 예외발생
				throw new Exception("Failed to register "+tempBranch+"(arg1) to UEFManager");
			}
		}
		catch (Exception e) {
			throw e;
		}
	}
	
	/**
	 * 명령 해석 스레드에서 사용되는 메소드로 UEFManager의 frame값을 변경한다.<br>
	 * 인자: arg0 - Long<br>
	 * 명령번호: 300
	 * @param order 처리할 명령
	 * @throws Exception 
	 * */
	private void SetFrame(Order order) {
		Map<String, Object> orderMap = order.getMap();
		long tempFrame = -1;
		
		try {
			tempFrame = (long) orderMap.get("arg0");
			
			// UEFManager의 프레임 설정
			super.setFrame(tempFrame);
		}
		catch (Exception e) {
			throw e;
		}
	}
	
	/**
	 * 명령 해석 스레드에서 사용되는 메소드로 default있는 Leaf를 삭제한다. <br>
	 * (default에서 Leaf를 삭제한다. 만약 삭제하지못한 경우 관리하는 모든 Branch에서 leaf를 찾아 제거한다.)<br>
	 * 인자: arg0 - ResutWaitter, arg1 - UEFLeaf<br>
	 * 명령번호: 200
	 * @param order 처리할 명령
	 * @throws Exception 
	 * */
	private void RemoveLeaf(Order order) throws Exception {
		Map<String, Object> orderMap = order.getMap();
		Iterator<UEFBranch> itr = manageCellList.iterator();
		
		ResultWaitter<UEFLeaf> resultWaitter = (ResultWaitter<UEFLeaf>) orderMap.get("arg0");
		UEFLeaf leaf = (UEFLeaf) orderMap.get("arg1");
		
		try {
			if(resultWaitter == null) throw new Exception("resultWaitter(arg0) is null");
			else if(leaf == null) throw new Exception("leaf(arg0) is null");
			
			/*
			 * default에서 leaf를 찾아 제거하고 성공하면 제거한 leaf important false로 설정
			 * */
			while(itr.hasNext()) {
				UEFBranch tempBranch = itr.next();
				
				if(tempBranch.getName().equals("default")) {
					UEFLeaf removeResult = (UEFLeaf) tempBranch.remove(leaf);
					
					if(removeResult != null) { // 제거를 실패하면 루프탈출
						commonRegist(removeResult, false);
						resultWaitter.setResult(removeResult);
						return;
					}
					else break;
				}
			}
			
			
			
			/*
			 * default에서 제거하지 못하면 일반 branch에서 leaf를 찾아 제거
			 * 실패하면 예외 송출
			 * */
			itr = manageCellList.iterator();
			
			while(itr.hasNext()) {
				UEFBranch tempBranch = itr.next();
				
				UEFLeaf removeResult = (UEFLeaf) tempBranch.remove(leaf);
				
				if(removeResult != null) {
					commonRegist(removeResult, false);
					removeResult.setParent(null);
					resultWaitter.setResult(removeResult);
					return ;
				}
			}
			
			throw new Exception("Couldn't find "+leaf+"(arg1) in UEFManager");
		}
		catch (Exception e) {
			
			resultWaitter.setResult(null);
			throw e;
		}
		
		
	}
	
	/**
	 * 명령 해석 스레드에서 사용되는 메소드로 관리하에있는 Branch를 삭제한다. (이미 존재하거나 같은 이름의 branch가 존재해야만 삭제가 가능하다.)<br>
	 * 인자: arg0 - ResutWaitter, arg1 - UEFBranch<br>
	 * 명령번호: 201, 202
	 * @param order 처리할 명령
	 * @throws Exception 
	 * */
	private void RemoveBranch(Order order) throws Exception {
		Map<String, Object> orderMap = order.getMap();
		Iterator<UEFBranch> itr = manageCellList.iterator();
		
		ResultWaitter<UEFBranch> resultWaitter = (ResultWaitter<UEFBranch>) orderMap.get("arg0");
		Object arg1 = orderMap.get("arg1");
		String name = null;
		UEFBranch branch = null;
		
		try{
			if(arg1 == null) throw new Exception("name or branch(arg1) is null");
			else if(arg1 instanceof String) name = (String) arg1;
			else if(arg1 instanceof UEFBranch) {
				branch = (UEFBranch) arg1;
			
				/*
				 * UEFManager에서 branch를 찾아제거하는 내용
				 * 못찾으면 예외발생
				 * 찾으면 제거시도
				 * 제거 실패하면 예외발생
				 * */
				if(manageCellList.contains(branch)) { // branch가 등록되있는지 확인
					try {
						if(manageCellList.remove(branch)) { // 삭제후 성공여부 확인
							commonRegist(branch, false);
							branch.setParent(null);
							resultWaitter.setResult(branch);
							
							return;
						}
						else { // 삭제 실패시
							throw new Exception("Couldn't remove "+branch+"(arg1) in UEFManager");
						}
					}
					catch (Exception e) { // 삭제중 예외발생
						throw e;
					}
				}
				else { // branch가 등록되어있지 않은경우
					throw new Exception("Couldn't find "+branch+"(arg1) in UEFManager");
				}
			}
			
			UEFBranch target = null;
			
			while(itr.hasNext()) { // 해당이름을 가진 branch탐색
				target = itr.next();
				
				if(target.getName().equals(name)) break;
			}
			
			if(target != null) { // 삭제할 branch를 찾은경우
				if(manageCellList.remove(target)) {
					commonRegist(target, false);
					target.setParent(null);
					resultWaitter.setResult(target);
				}
				else { // 삭제 실패시
					throw new Exception("Couldn't remove "+name+"(arg1) in UEFManager");
				}
			}
			else {
				throw new Exception("Couldn't find "+name+"(arg1) in UEFManager");
			}
		}
		catch (Exception e) {
			resultWaitter.setResult(null);
			throw e;
		}
	}
	
	/**
	 * 명령 해석 스레드에서 사용되는 메소드로 관리하에있는 Branch에 존재하는 Leaf를 삭제한다. (인자로 들어온  branch가 존재하지 않으면 Leaf 삭제 불가능)<br>
	 * 인자: arg0 - ResutWaitter, arg1 - UEFLeaf, arg2 - UEFBranch<br>
	 * 명령번호: 203
	 * @param order 처리할 명령
	 * @throws Exception 
	 * */
	private void RemoveLeafByBranchObject(Order order) throws Exception {
		Map<String, Object> orderMap = order.getMap();
		ResultWaitter<UEFLeaf> resultWaitter = (ResultWaitter<UEFLeaf>) orderMap.get("arg0");
		UEFLeaf leaf = (UEFLeaf) orderMap.get("arg1");
		UEFBranch branch = (UEFBranch) orderMap.get("arg2");
		
		try {
			if(resultWaitter == null) {
				throw new Exception("resultWaitter(arg0) is null");
			}
			else if(leaf == null) { // leaf가 null이면 예외발생
				throw new Exception("leaf(arg1) is null");
			}
			else if(branch == null) { // branch가 null이면 예외발생
				throw new Exception("branch(arg2) is null");
			}
			
			if(manageCellList.contains(branch)) { // branch가 UEFManager에 등록되어있는지 확인
				if(branch.contains(leaf)) { // leaf가 branch에 들어있는지 확인
					UEFLeaf removeResult = (UEFLeaf) branch.remove(leaf);
					
					if(removeResult != null) {
						commonRegist(leaf, false);
						removeResult.setParent(null);
						resultWaitter.setResult(removeResult);
					}
					else { // leaf를 branch에서 제거하는데 실패했으므로 예외발생
						throw new Exception("Failed to remove leaf "+leaf+" from "+branch);
					}
				}
				else { // leaf가 branch에 등록되어있지 않으므로 예외발생
					throw new Exception("Leaf "+leaf+" isn't registered in "+branch);
				}	
			}
			else { // branch가 UEFManager에 등록되어있지 않으므로 예외발생
				throw new Exception("Branch "+branch+" isn't registered in UEFManager");
			}
		}
		catch (Exception e) {
			resultWaitter.setResult(null);
			throw e;
		}
	}
	
	/**
	 * 명령 해석 스레드에서 사용되는 메소드로 관리하에있는 Branch에 존재하는 Leaf를 삭제한다. (인자로 들어온  이름이 존재하지 않으면 Leaf 삭제 불가능)<br>
	 * 인자: arg0 - ResutWaitter, arg1 - UEFLeaf, arg2 - String<br>
	 * 명령번호: 204
	 * @param order 처리할 명령
	 * @throws Exception 
	 * */
	private void RemoveLeafByBranchName(Order order) throws Exception {
		Map<String, Object> orderMap = order.getMap();
		Iterator<UEFBranch> itr = manageCellList.iterator();
		ResultWaitter<UEFLeaf> resultWaitter = (ResultWaitter<UEFLeaf>) orderMap.get("arg0");
		UEFLeaf leaf = (UEFLeaf) orderMap.get("arg1");
		String branchName = (String) orderMap.get("arg2");
		
		try {
			if(resultWaitter == null) {
				throw new Exception("resultWaitter(arg0) is null");
			}
			else if(leaf == null) { // leaf가 null이면 예외발생
				throw new Exception("leaf(arg1) is null");
			}
			else if(branchName == null) { // branch가 null이면 예외발생
				throw new Exception("branchName(arg2) is null");
			}
			
			UEFBranch target = null;
			
			while(itr.hasNext()) {
				UEFBranch tempBranch = itr.next();
				
				if(tempBranch.getName().equals(branchName)) {
					target = tempBranch;
				}
			}
			
			if(target != null) {
				if(target.contains(leaf)) { // leaf가 branch에 들어있는지 확인
					UEFLeaf removeResult = (UEFLeaf) target.remove(leaf);
					
					if(removeResult != null) {
						commonRegist(leaf, false);
						removeResult.setParent(null);
						resultWaitter.setResult(removeResult);
					}
					else { // leaf를 branch에서 제거하는데 실패했으므로 예외발생
						throw new Exception("Failed to remove leaf "+leaf+" from "+target);
					}
				}
				else { // leaf가 branch에 등록되어있지 않으므로 예외발생
					throw new Exception("Leaf "+leaf+" isn't registered in "+target);
				}	
			}
			else {
				throw new Exception("There is no branch registered in UEFManager with that ("+branchName+")");
			}
			
		}
		catch (Exception e) {
			resultWaitter.setResult(null);
			throw e;
		}
	}
	
	/**
	 * 명령 해석 스레드에서 사용되는 메소드로 인자로 들어온 문자열을 패턴으로 사용해 이름이 일치하는 Branch를 실행한다. (모두 실행하려면 "^.*$") <br>
	 * 인자: arg0 - String<br>
	 * 명령번호: 400, 401, 402, 403
	 * @param order 처리할 명령
	 * @throws Exception 
	 * */
	private void RequestTrigger(Order order) throws Exception {
		Map<String, Object> orderMap = order.getMap();
		Iterator<UEFBranch> itr = manageCellList.iterator();
		String pattern = (String) orderMap.get("arg0");
		
		String result = null;
		String requestTriggerName = null;
		Level level = null;
		
		if(pattern == null) throw new Exception("pattern(arg0) is null");
		
		while(itr.hasNext()) {
			UEFBranch tempBranch = itr.next();
			
			if(tempBranch == null) continue;
			else if(tempBranch.getName().matches(pattern) && tempBranch.getCurrentTriggerStatus() != RoutineTriggerStatus.SHUTDOWN){
				// 이름과 pattern이 일치하고 종료되지 않은 상태라면 실행한다.
				try {
					switch (order.getOrderNumber()) {
						case 400:
							requestTriggerName = "RequestExecute";
							tempBranch.requestExecute(); break;
							
						case 401:
							requestTriggerName = "RequestPause";
							tempBranch.requestPause(); break;
							
						case 402:
							requestTriggerName = "RequestStop";
							tempBranch.requestStop(); break;
							
						case 403:
							requestTriggerName = "RequestShutdown";
							tempBranch.requestShutdown(); break;
							
						default: break;
					}
					
					level = Level.FINE;
					result = "successed.";
					
				}
				catch (AccessControlException e1) {
					level = Level.WARNING;
					result = "failed.("+e1.getMessage()+")";
				}
				finally {
					log(level, tempBranch+" "+requestTriggerName+" "+result);
				}
			}
		}
	}
	
	/**
	 * 명령 해석 스레드에서 사용되는 메소드로 인자로 들어온 문자열을 패턴으로 사용해 이름이 일치하는 Branch를 실행한다. (모두 실행하려면 "^.*$") <br>
	 * 인자: arg0 - boolean<br>
	 * 명령번호: 302
	 * @param order 처리할 명령
	 * @throws Exception 
	 * */
	private void ExitUEFManager(Order order) throws Exception {
		try {
			if(isEnd != true) {
				boolean systemEnd = (boolean) order.getMap().get("arg0");
				ExecutorService executor = Executors.newCachedThreadPool();
				Runnable runnable = new Runnable() {
					@Override
					public void run() {
						if(isEnd != true) {
							log(Level.INFO, "UEF 라이브러리를 종료합니다.");
							
							try {
								UEFManager.getInstance().shutdownHook.start();					
								UEFManager.getInstance().shutdownHook.join();
							} catch (InterruptedException e) {
								e.printStackTrace();
							}
							finally {
								isEnd = true;
								executor.shutdown();
								if(systemEnd) System.exit(0);
							}
						}
					}
				};
				
				executor.execute(runnable);
				
			}
			else{
				throw new Exception("이미 UEF 라이브러리가 종료됬습니다.");
			}
		}
		catch (Exception e) {
			throw e;
		}
	}
	
	/**
	 * 현재 UEFManager에 등록된 모든 UEFBranch의 정보를 리턴하는 메소드<br>
	 * 
	 * @param isPrint 정보를 콘솔에 출력할지 정하는 값
	 * @return UEFBranch:getCurrentTriggerStatus:getFrame\n<br>
	 * UEFBranch:getCurrentTriggerStatus:getFrame\n<br>
	 * UEFBranch:getCurrentTriggerStatus:getFrame\n....
	 * */
	public String printAllManageList(boolean isPrint) {
		StringBuffer sb = new StringBuffer();
		
		synchronized (manageCellList) {
			Iterator<UEFBranch> itr = manageCellList.iterator();
			
			while(itr.hasNext()) {
				UEFBranch tempBranch = itr.next();
				
				sb.append(tempBranch+":"+tempBranch.getCurrentTriggerStatus()+":"+tempBranch.getFrame()+":\n");
			}
		}
		
		if(isPrint) System.out.print(sb.toString());
		
		return sb.toString();
	}

	
	/**
	 * UEF라이브러리를 정상적으로 종료시킬 때 사용하는 메소드로 org.UEF.core.UEFCell을 상속한 모든 클래스의 인스턴스들을 종료시킨다.<br>
	 * 또한 정상종료될 경우 ShutdownHook을 통해 라이브러리의 종료가 진행되며 요청한 스트림으로 진행상황을 출력한다.
	 * @param endProcess true일 경우 UEF라이브러리를 종료하고나서 System.exit(0)를 호출한다.
	 * */
	public synchronized void exit(boolean endProcess) {
		if(doNotCallThis4exit == false) {
			UEFManager.getInstance().sendOrder(CoreOrderNumber.EXIT_UEFMANAGER, endProcess);
			doNotCallThis4exit = true;
		}
		
	}
	
	
	/*
	 * ====================================================================================================================================
	 * 
	 * 
	 * Deprecated Method (사용중지 메소드)
	 * thread: run
	 * List: add, addList, remove, remioveList, removeAll [새로운 리스트(전체, 관리)를 사용할 예정]
	 * Setter: parent, Trigger, UseParentFrame, UseParentLogger [현재 트리거 상태와 부모를 사용하지 않을예정(Logger와 Frame 상속사용금지)]
	 * Routineable: init, ready, execute, pause, stop, destroy [따로 스레드를 사용하며 실행을 조작할 예정]
	 * Shutdown Maintain: ThisIsVeryImportant, ThisIsNotVeryImportant, IsThisVeryImportant [시스템 종료시 Hooking을 통해 따로 관리할 예정]
	 * RoutineTrigger: RequestExecute, RequestPause, RequestStop, RequestShutdown [기존의 리스트를 사용하지 않게되면서 메소드 폐기]
	 * 
	 * 
	 * ====================================================================================================================================
	 */

	@Override
	@Deprecated
	public void run() {
		
	}

	@Override
	@Deprecated
	protected void setTrigger(RoutineTriggerStatus afterStatus) {
		
	}
	
	@Override
	@Deprecated
	protected void setParent(UEFCell parent) throws AccessControlException, ClassNotFoundException {
		
	}
	
	@Override
	@Deprecated
	public void setUseParentFrame(boolean useParentFrame) {
		
	}
	
	@Override
	@Deprecated
	public void setUseParentLogeer(boolean useParentLogger) {

	}
	
	@Override
	@Deprecated
	protected void freeBlocked() {
		
	}
}
