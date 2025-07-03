package com.hmdp.utils;

import java.io.Serializable;
import java.time.LocalDateTime;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class RedisData<T> implements Serializable {
	private LocalDateTime expireTime;
	private T data;
}
