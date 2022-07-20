package org.UEF.others;

import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.locks.ReentrantLock;


/**
 * UEFManager의 명령 해석 스레드를 위해 제작된 클래스로 T를 보관하는{@link ConcurrentLinkedQueue}를 두개 가진다.
 * */
public class DoubleQueue<T> {
	private ConcurrentLinkedQueue<T> queue1 = new ConcurrentLinkedQueue<T>();
	private ConcurrentLinkedQueue<T> queue2 = new ConcurrentLinkedQueue<T>();
	private boolean isUsedFirstQueue = false;
	private ReentrantLock lock = new ReentrantLock();
	
	/**
	 * 해당 메소드는 현재 사용되지 않는 Queue를 리턴하는 메소드이다.<br>
	 * (Queue의 사용권을 빼앗지 않기 때문에 해당 메소드를 사용해 Queue를 사용하고 다시 빌리는 경우 이전에 빌린 Queue는 사용하지 않아야한다.)
	 * @return 현재 사용되지 않는 Queue
	 * */
	public Queue<T> borrowQueue(){
		Queue<T> queue = null;
		
		try {
			lock.lock();
			
			isUsedFirstQueue = !isUsedFirstQueue;
			
			// 맨처음에는 queue1을 사용함
			if(isUsedFirstQueue) queue = queue1;
			else queue = queue2;
		}
		catch (Exception e) {
			e.printStackTrace();
		}
		finally {
			lock.unlock();
		}
		
		return queue;
	}
	
	/**
	 * 현재 사용되지 않는 Queue에 T를 집어넣는 메소드
	 * @param T 넣고자 하는 객체
	 * @return order를 제대로 삽입한 경우 true, 실패한 경우 false
	 * */
	public boolean offer(T t) {
		boolean result = false;
		
		try {
			lock.lock();
			
			if(isUsedFirstQueue) result = queue2.offer(t);
			else result = queue1.offer(t);
		}
		catch (Exception e) {
			e.printStackTrace();
		}
		finally {
			lock.unlock();
		}
		
		return result;
	}
	
	/**
	 * 현재 값이 존재하는 Queue가 있는지 리턴하는 메소드
	 * @return Queue가 둘중에 하나라도 안 비어있으면 false, 둘다 비었으면 true
	 * */
	public boolean isEmpty() {
		return queue1.isEmpty() && queue2.isEmpty(); 
	}
}
