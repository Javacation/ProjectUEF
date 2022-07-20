package org.UEF.itf;

import java.util.logging.Level;

import org.UEF.core.UEFCell;

public interface Logable {
	/**
	 * 해당 객체에서 로그를 남기는 메소드로 UEFCell에서 로그작업을 위해 사용하며 {@link UEFCell setUseParentLogger()}로 설정하여 Parent의 Logger를 사용한다.<br>
	 * yyyy-MM-dd HH:mm:ss.SSS logLevel getName: contents\n 의 형태로 제작된다.<br>
	 * 만일 로거에 핸들러가 등록되있지 않으면서 부모 로거 사용도 할 수 없는 경우 형식을 따라서 콘솔에 출력한다.
	 * 
	 * @param logLevel 로그의 레벨
	 * @param contents 로그의 내용
	 * */
	public void log(Level logLevel, String contents);
}
