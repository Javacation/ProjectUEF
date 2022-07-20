package org.UEF.others;

import java.util.HashMap;
import java.util.Map;

/**
 * {@link org.UEF.core.UEFManager}의 명령 해석 스레드를 위해 제작된 클래스로 명령정보를 가진다.<br><br>
 * {@link Plan}과의 차이점
 * <ul>
 * <li>즉각적으로 실행시킬 수 있는 람다 인터페이스를 가지고 있지않다.</li>
 * <li>태그를 가지지 않고 사용자가 지정한 명령번호만을 가진다.</li>
 * <li>람다 인터페이스를 즉석에서 구현하지 않기 때문에 처리에 필요한 정보를 사용자가 삽입해야한다.</li>
 * <li>해당 클래스의 명령번호를 보고 정보를 처리할 스레드를 따로 직접 구현해야한다.</li>
 * </ul> 
 * 
 * */
public class Order {
	private long id = 0l;
	private int orderNumber = 0;
	private String requestThread = null;
	private boolean isEnd = false;
	private Map<String, Object> map = new HashMap<String, Object>();
	
	
	public Order(int orderNumber) {
		id = System.nanoTime();
		this.orderNumber = orderNumber;
		requestThread = Thread.currentThread().getName();
	}
	
	/**
	 * Order의 map에 값을 넣는 메소드
	 * @param key 키 값
	 * @param value 넣을내용
	 * */
	public void put(String key, Object value) {
		if(key != null && value != null) map.put(key, value);
	}
	
	/**
	 * Order의 명령번호를 리턴하는 메소드
	 * @return 명령번호
	 * */
	public int getOrderNumber() {
		return orderNumber;
	}
	
	/**
	 * Order의 고유번호를 리턴하는 메소드
	 * @return 고유번호
	 * */
	public long getId() {
		return id;
	}

	/**
	 * Order의 map을 리턴하는 메소드
	 * @return map[String, Object]
	 * */
	public Map<String, Object> getMap() {
		return map;
	}

	/**
	 * 해당 Order를 제작한 스레드 이름을 리턴하는 메소드
	 * @return 스레드 이름
	 * */
	public String getRequestThread() {
		return requestThread;
	}
	
	@Override
	public String toString() {
		
		return "[Id: "+id+", orderNumber: "+orderNumber+", requestThread: "+requestThread+"]";
	}
}
