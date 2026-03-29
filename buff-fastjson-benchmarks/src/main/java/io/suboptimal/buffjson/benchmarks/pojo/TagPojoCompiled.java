package io.suboptimal.buffjson.benchmarks.pojo;

import com.alibaba.fastjson2.annotation.JSONCompiled;

@JSONCompiled
public class TagPojoCompiled {

	private String key;
	private String value;

	public String getKey() {
		return key;
	}

	public void setKey(String key) {
		this.key = key;
	}

	public String getValue() {
		return value;
	}

	public void setValue(String value) {
		this.value = value;
	}
}
