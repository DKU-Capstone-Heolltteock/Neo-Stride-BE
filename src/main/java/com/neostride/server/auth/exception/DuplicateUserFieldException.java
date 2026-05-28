package com.neostride.server.auth.exception;

public class DuplicateUserFieldException extends RuntimeException {

	public enum Field {
		EMAIL,
		NAME,
		NICKNAME
	}

	private final Field field;

	public DuplicateUserFieldException(Field field, String message) {
		super(message);
		this.field = field;
	}

	public Field field() {
		return field;
	}

	public static DuplicateUserFieldException email() {
		return new DuplicateUserFieldException(Field.EMAIL, "이미 사용 중인 이메일입니다.");
	}

	public static DuplicateUserFieldException name() {
		return new DuplicateUserFieldException(Field.NAME, "이미 사용 중인 사용자 이름입니다.");
	}

	public static DuplicateUserFieldException nickname() {
		return new DuplicateUserFieldException(Field.NICKNAME, "이미 사용 중인 닉네임입니다.");
	}
}
