package org.UEF.core;

import java.lang.Thread.State;
import java.security.AccessControlException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Vector;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;
import java.util.logging.ConsoleHandler;
import java.util.logging.Formatter;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogManager;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

import org.UEF.enu.RoutineTriggerStatus;
import org.UEF.itf.Logable;
import org.UEF.itf.RoutineTrigger;
import org.UEF.itf.Routineable;

/**
 * UEF는 Universal Framework의 약자로 유니티의 스레드 실행 사이클을 참고하여 만든 클래스로<br>
 * 사용자가 스레드를 관리할 필요 없이 {@link Routineable}의 메소드들만 재정의 하여 기능을 구현만 하게 만들기 위한 추상 클래스이다.<br>
 * 모든 UEFCell객체는 UEFManager관리하에 있지 않더라도 생성시 자동으로 리스트에 추가되도록 설계되었으며 잃어버려도 이름을 기억하고 있으면 찾아낼 수 있다.
 * */
public abstract class UEFCell implements RoutineTrigger, Runnable, Logable{
	// 객체의 이름(생성자에서만 지정가능)
	private String 
		name = null;
	
	// 객체의 부모
	private UEFCell 
		parent = null;
	
	// 객체의 스레드
	private Thread 
		uefThread = null;
	
	// 객체의 현재 트리거 상태
	private RoutineTriggerStatus 
		currentTriggerStatus = RoutineTriggerStatus.NEW;
	
	// 객체의 기록 파악용 로거 (멀티스레드 환경에서 안전함)
	private Logger 
		logger = null;
	
	// 객체의 락으로 메소드의 안전한 사용을 위해 준비함 (예를들어 자식의 추가나 부모의 변경과 같은)
	private ReentrantLock 
		lock = null;
	
	// 락 상태에서 공통으로 사용하기 위한 Condition
	private List<Condition>
		commonConds = null;
	
	// 객체 사이클의 초당 실행 횟수로 처음 생성시 60으로 지정됨
	private long 
		frame = DEAFULT_FRAME;
	
	/*
	 * 시스템 종료시 반드시 pause-stop-destroy를 거쳐야 하는지를 결정하는 변수
	 * 객체가 부모의 초당 실행단위를 사용할 것인지 여부
	 * 객체가 부모의 로거에 포함되어 사용될 것인지 여부
	 * */
	private boolean 
		waitForEnd = false,
		useParentFrame = false,
		useParentLogger = false;
	
	/*
	 * 최대 프레임
	 * 모든 사이클의 기본프레임
	 * 최소 프레임
	 * 1초를 나노초로 바꾼 값으로 최소대기시간 계산에 사용된다.
	 * */
	public static long 
		MAX_FRAME = 1_000_000_000l,
		DEAFULT_FRAME = 60,
		MIN_FRAME = 1,
		ONE_NANO_SECONED = 1_000_000_000l;
	
	/**
	 * 로그작업에 사용될 공통 포맷 변수
	 * */
	public static Formatter COMMON_FORMATTER = new Formatter() {
		@Override
		public String getHead(Handler h) {
			StringBuffer buffer = new StringBuffer();
			
			buffer.append(calcDate(System.currentTimeMillis()));
			buffer.append(" start\n");
			
			return buffer.toString();
		}
		
		@Override
		public String format(LogRecord record) {
			StringBuffer buffer = new StringBuffer();
			
			// 시간추가
			buffer.append(calcDate(record.getMillis()));
			buffer.append(' ');
			
			// 레벨추가
			buffer.append(record.getLevel());
			buffer.append(' ');
			
			// 메시지 추가
			buffer.append(record.getMessage());
			buffer.append('\n');
			
			return buffer.toString();
		}
		
		private String calcDate(long millisecs) {
	        SimpleDateFormat date_format = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS");
	        Date resultdate = new Date(millisecs);
	        return date_format.format(resultdate);
	    }
		
		@Override
		public String getTail(Handler h) {
			StringBuffer buffer = new StringBuffer();
			
			buffer.append(calcDate(System.currentTimeMillis()));
			buffer.append(" end\n");
			
			return buffer.toString();
		}
	};
	
	/**
	 * UEFCell의 생성자로 (이름, 부모 프레임 사용여부, 부모 로거 사용여부)를 결정하고
	 * Logger, ReentrantLock, Thread의 생성을 진행한다.
	 * @param name 객체이름
	 * @param useParentFrame 객체의 부모 프레임 사용UEFManager여부
	 * @param useParentLogger 객체의 부모 로거 사용여부
	 * */
	protected UEFCell(String name, boolean useParentFrame, boolean useParentLogger) {
		if(this instanceof UEFManager) {
			this.name = name;
			
			// 로거 생성
			logger = Logger.getLogger(getClass().getName()+":"+getName()+":"+hashCode());
			// 락생성
			lock = new ReentrantLock();
			// Condition생성
			commonConds = new Vector<Condition>();
		}
		else {
			this.name = name;
			
			// 로거 생성
			logger = Logger.getLogger(getClass().getName()+":"+getName()+":"+hashCode());
			// 락생성
			lock = new ReentrantLock();
			// Condition생성
			commonConds = new Vector<Condition>();
			
			setUseParentFrame(useParentFrame);
			setUseParentLogeer(useParentLogger);
			
			/*
			 * 스레드 생성 및 데몬으로 등록
			 * */
			uefThread = new Thread(this);
			uefThread.setDaemon(true);
			uefThread.setName(getClass().getPackageName()+"."+getName()+"-Thread");
			//uefThread.start();
			
			if(!(this instanceof UEFManager)) UEFManager.addCell(this);
		}

	}
	
	/**
	 * UEFCell의 생성자로 this(이름, true, true)를 호출한다.
	 * @param name 객체이름
	 * */
	protected UEFCell(String name) {
		this(name, true, true);
	}
	
	/**
	 * 현재 트리거 상태를 리턴하는 메소드
	 * @return 현재 트리거 상태
	 * */
	public final RoutineTriggerStatus getCurrentTriggerStatus() {
		return currentTriggerStatus;
	}
	
	/**
	 * 현재 트리거를 파라미터 값으로 바꾸는 메소드
	 * @param afterStatus 바꿀 다음 상태
	 * */
	protected void setTrigger(RoutineTriggerStatus afterStatus) {
		currentTriggerStatus = afterStatus;
	}
	
	/**
	 * 객체의 프레임을 리턴하는 메소드, 부모 프레임 사용이 활성화되고 부모가 존재하는 경우 부모프레임 리턴
	 * @return 현재 지정된 프레임
	 * */
	public long getFrame() {
		long result = 0;
		
		if(isUseParentFrame() && getParent() != null) result = getParent().getFrame();
		else result = frame;
		
		return result;
	}
	
	/**
	 * 객체의 프레임을 지정하는 메소드<br>
	 * <ul>
	 * <li>MIN_FRAME = 1, MAX_FRAME = 1e+9</li>
	 * <li></li>
	 * <li><b>frame < 1</b> : this.frame = MIN_FRAME</li>
	 * <li><b>1 <= frame <= 1e+9</b> : this.frame = frame</li>
	 * <li><b>frame > 1e+9</b> : this.frame = MAX_FRAME</li>
	 * </ul>
	 * @param frame 지정할 프레임 값
	 * */
	public void setFrame(long frame) {
		setUseParentFrame(false);
		
		if(frame < MIN_FRAME) {
			this.frame = MIN_FRAME;
		}
		else if(frame > MAX_FRAME) {
			this.frame = MAX_FRAME;
		}
		else {
			this.frame = frame;
		}
	}
	
	/**
	 * 객체의 이름을 리턴하는 메소드
	 * @return 이름
	 * */
	public String getName() {
		return name;
	}

	/**
	 * 객체의 부모를 리턴하는 메소드
	 * @return 부모객체
	 * */
	public UEFCell getParent() {
		return parent;
	}
	
	/**
	 * 객체의 부모를 지정하는 메소드(한 번 지정하면 다시는 못바꿈)<br>
	 * 부모 프레임, 로거의 사용여부가 true일 경우 부모를 지정함과 동시에 값을 연동한다.
	 * (UEFBranch만이 호출할 수 있음, 그외에 호출시 예외발생)
	 * 
	 * @param parent 부모 객체
	 * @throws Exception 
	 * @exception AccessControlException 이미 부모가 지정되어있는데 호출하는 경우 발생
	 * @throws ClassNotFoundException 해당 메소드를 호출한 메소드가 속한 클래스를 찾지못한경우
	 * */
	
	 protected void setParent(UEFCell parent) throws AccessControlException, ClassNotFoundException{
		UEFCell tempPar = this.parent;
		StringBuffer callerClassName = new StringBuffer(Thread.currentThread().getStackTrace()[2].getClassName());
		Class<?> callerClass = Class.forName(callerClassName.toString()), 
				compareClass1 = Class.forName(org.UEF.core.UEFBranch.class.getCanonicalName());
		
		
		// 해당 객체가 부모를 가졌고 그 부모가 UEFManager가 아니면서 해당 메소드를 호출한 클래스가 UEFBrnach가 아닌경우 예외호출
		if(tempPar != null // 현재부모가 존재하면 다음 조건으로
				&& !(tempPar instanceof UEFManager)  // 현재부모가  UEFManager이 아니면 다음 조건으로
				&& !(compareClass1.isAssignableFrom(callerClass)) // 이 메소드를 호출한 클래스가 UEFBranch를 상속하지 않으면 통과
				) {
			if(compareClass1.isAssignableFrom(callerClass))
				throw new AccessControlException("setParent()를 호출한 객체가 UEFBranch를 상속하지 않았습니다. ("+callerClassName+")");
			else if(this.getParent() != null)
				throw new AccessControlException(this+" already has "+this.getParent()+" as a parent.");
			else
				throw new AccessControlException("setParent() 호출중 문제가 발생했습니다.");
			
		}
		else {
			if(parent instanceof UEFBranch || !(parent instanceof UEFLeaf)) { 
				// 인자가 UEFBranch를 상속하거나 UEFLeaf를 상속하지 않으면 등록가능
				// 따라서 null을 넣을경우 상황에 따라 부모로 null 등록가능
				this.parent = parent;
				
				setUseParentFrame(isUseParentFrame());
				setUseParentLogeer(isUseParentLogger());
			}
			
		}
	}
	
	/**
	 * 부모 객체 프레임 사용여부 리턴 메소드
	 * @return 부모 객체 프레임 사용여부
	 * */
	public boolean isUseParentFrame() {
		return useParentFrame;
	}
	
	/**
	 * 부모 객체 프레임 사용여부 지정 메소드<br>
	 * true지정 시 부모가 있으면 값을 연동한다.
	 * 
	 *  @param useParentFrame 부모 객체 프레임 사용여부
	 * */
	public void setUseParentFrame(boolean useParentFrame) {
		this.useParentFrame = useParentFrame;
		
		if(useParentFrame && getParent() != null) {
			this.frame = getParent().getFrame();
		}
	}

	/**
	 * 부모 객체 로거 사용여부 리턴 메소드
	 * @return 부모 객체 로거 사용여부
	 * */
	public boolean isUseParentLogger() {
		return useParentLogger;
	}

	/**
	 * 부모 객체 로거 사용여부 지정 메소드<br>
	 * true지정 시 부모가 있으면 값을 연동한다. (부모가 없으면 useParentLogger가 true여도 자신의 로거를 사용한다.)
	 * 
	 *  @param useParentLogger 부모 객체 로거 사용여부
	 * */
	public void setUseParentLogeer(boolean useParentLogger) {
		this.useParentLogger = useParentLogger;
		
		if(useParentLogger && getParent() != null) {
			logger.setUseParentHandlers(true);
			
			/*
			if(logger.getParent() == null ||
					!logger.getParent().equals(getParent().getLogger())) {
				logger.setParent(getParent().getLogger());
			}
			*/	
			
			if(!logger.getParent().equals(getParent().getLogger())) {
				logger.setParent(getParent().getLogger());
				
				//if(logger.getParent().equals(getParent().getLogger())) System.out.println(this+"의 로거등록 성공");
			}
			//else System.out.println(this+"는 이미 부모 로거 등록을 했습니다.");
		}
		else { // 사용이 true여도 부모가 없으니 일단 부모로거 사용중지
			logger.setUseParentHandlers(false);
		}
	}
	
	/**
	 * 객체가 가진 Logger를 리턴하는 메소드
	 * 
	 * @return 객체가 가진 Logger
	 * */
	public Logger getLogger() {
		return logger;
	}
	
	/**
	 * 객체가 가진 ReentrantLock을 리턴하는 메소드
	 * 
	 * @return 객체가 가진 ReentrantLock
	 * */
	protected ReentrantLock getLock() {
		return lock;
	}
	
	/**
	 * 객체가 가진 ReentrantLock을 통해 만들어진 Condition을 리턴하는 메소드<br>
	 * 번호를 지정하여 Condition을 만들 수 있음 ex) index가 100이면 100개를 생성해서 100번째를 리턴
	 * 
	 * @param index ReentrantLock에서 만들어진 Condition번호
	 * @return 만들어진 Condition
	 * @throws ArrayIndexOutOfBoundsException index값이 0보다 작은경우 발생
	 * */
	protected Condition getCommonCondition(int index) throws ArrayIndexOutOfBoundsException{
		Condition result = null;
		
		if(index < 0) {
			throw new ArrayIndexOutOfBoundsException();
		}
		else {
			if(commonConds.size() < (index + 1)) {
				for(int i = 0; i < (index + 1) - commonConds.size(); i++) 
					commonConds.add(lock.newCondition());
			}
			
			result = commonConds.get(index);
		}
		
		return result;
	}
	
	public void log(Level logLevel, String contents) {
		if(logLevel != null && contents != null) {
			contents = contents.trim()+"\n";
			
			if(logLevel == Level.ALL) logLevel = Level.FINEST;
			else if(logLevel == Level.OFF) logLevel = Level.SEVERE;

			logger.log(logLevel, this+": "+contents);
		}
	}
	
	/**
	 * RoutineTrigger의 메소드를 실행할 때 재지정한 Routineable의 메소드에서 사용된 블로킹 메소드에서 탈출하기 위한 메소드<br>
	 * 한 번의 블로킹 메소드는 확실하게 탈출하지만 그 다음 블로킹 메소드의 탈출은 장담하지 못함
	 * */
	protected void freeBlocked() {
		try {
			switch (uefThread.getState()) {
				case WAITING:
				case TIMED_WAITING:
				case BLOCKED:
					
					// 상태가 해제될 때까지 반복은 하는데 정확하게 작동하지는 않음
					while(uefThread.getState() == State.BLOCKED) {
						uefThread.interrupt();
						
						try {
							// 1 마이크로초 대기
							Thread.sleep(0, 1000);
						}
						catch (Exception e) {
							e.printStackTrace();
							break;
						}
					}
				break;
				
				default:
				break;
			
			}
		}
		catch (Exception e) {
			e.printStackTrace();
			
			/*
			 * 로그작성
			 * */
		}
	}
	
	/**
	 * 만약 스레드가 시작되지 않은 상태라면 스레드를 시작하는 메소드
	 * */
	private void checkStart() {
		try {
			if(uefThread.getState() == State.NEW) {
				uefThread.start();
				
				// 시작후 스레드 안정화를 위해 1마이크로초 대기
				Thread.sleep(0, 1000);
			}
		}
		catch (Exception e) {
			e.printStackTrace();
		}
	}
	
	
	public void setWaitForEnd(boolean waitForEnd) {
		this.waitForEnd = waitForEnd;
	}
	
	/**
	 * 이 객체가 반드시 종료되는것을 기다려야 하는지 여부를 리턴하는 메소드
	 * @return true일 경우 join으로 종료를 기다려줘야하는 상태
	 * */
	public boolean isWaitForEnd() {
		return waitForEnd;
	}
	
	/**
	 * 해당 UEFCell의 소속과 이름을 '.'으로 구분하여 문장열로 리턴하는 메소드
	 * @return 소속.....이름
	 * */
	public String getPath() {
		StringBuffer resultPath = new StringBuffer(getName());
		UEFCell tempParent = getParent();
		
		while (tempParent != null) {
			resultPath.insert(0, ".");
			resultPath.insert(0, tempParent.getName());
			
			tempParent = tempParent.getParent();
		}
		
		
		return resultPath.toString();
	}
	
	/**
	 * 해당 스레드가 종료될 때까지 대기하는 메소드
	 * */
	public void join() {
		try {
			uefThread.join();
		}
		catch (Exception e) {
			e.printStackTrace();
		}
	}
	
	@Override
	public String toString() {
		
		return getPath();
	}
	
	@Override
	public void requestExecute() throws AccessControlException {
		if(currentTriggerStatus == RoutineTriggerStatus.SHUTDOWN) 
			throw new AccessControlException("is "+getName()+" already shutdown");
		else if(currentTriggerStatus == RoutineTriggerStatus.EXECUTE)
			throw new AccessControlException("is "+getName()+" already execute");
		
		checkStart();
	}
	
	@Override
	public void requestPause() throws AccessControlException {
		if(currentTriggerStatus == RoutineTriggerStatus.SHUTDOWN) 
			throw new AccessControlException("is "+getName()+" already shutdown");
		else if(currentTriggerStatus == RoutineTriggerStatus.PAUSE)
			throw new AccessControlException("is "+getName()+" already pause");
		
		checkStart();
	}
	
	@Override
	public void requestShutdown() throws AccessControlException {
		if(currentTriggerStatus == RoutineTriggerStatus.SHUTDOWN) 
			throw new AccessControlException("is "+getName()+" already shutdown");
		
		checkStart();
	}
	
	@Override
	public void requestStop() throws AccessControlException {
		if(currentTriggerStatus == RoutineTriggerStatus.SHUTDOWN) 
			throw new AccessControlException("is "+getName()+" already shutdown");
		else if(currentTriggerStatus == RoutineTriggerStatus.STOP)
			throw new AccessControlException("is "+getName()+" already stop");
		
		checkStart();
	}
}
