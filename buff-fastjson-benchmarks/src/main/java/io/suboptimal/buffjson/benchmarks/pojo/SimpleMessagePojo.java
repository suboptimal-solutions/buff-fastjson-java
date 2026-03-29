package io.suboptimal.buffjson.benchmarks.pojo;

public class SimpleMessagePojo {

	private String name;
	private int id;
	private long timestampMillis;
	private double score;
	private boolean active;
	private String status;

	public String getName() {
		return name;
	}

	public void setName(String name) {
		this.name = name;
	}

	public int getId() {
		return id;
	}

	public void setId(int id) {
		this.id = id;
	}

	public long getTimestampMillis() {
		return timestampMillis;
	}

	public void setTimestampMillis(long timestampMillis) {
		this.timestampMillis = timestampMillis;
	}

	public double getScore() {
		return score;
	}

	public void setScore(double score) {
		this.score = score;
	}

	public boolean isActive() {
		return active;
	}

	public void setActive(boolean active) {
		this.active = active;
	}

	public String getStatus() {
		return status;
	}

	public void setStatus(String status) {
		this.status = status;
	}
}
