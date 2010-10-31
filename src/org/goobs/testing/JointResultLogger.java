package org.goobs.testing;

public class JointResultLogger extends ResultLogger{
	private ResultLogger[] loggers;
	public JointResultLogger(ResultLogger ... loggers){
		this.loggers = loggers;
	}
	@Override
	public void add(int index, Object guess, Object gold) {
		for(ResultLogger l : loggers){
			l.add(index, guess, gold);
		}
	}
	@Override
	public void addGlobalString(String name, Object value) {
		for(ResultLogger l : loggers){
			l.addGlobalString(name, value);
		}
	}
	@Override
	public void addLocalString(int index, String name, Object value) {
		for(ResultLogger l : loggers){
			l.addLocalString(index, name, value);
		}
	}
	@Override
	public String getIndexPath() {
		throw new UnsupportedOperationException("Cannot retreive single index path from multiple loggers");
	}
	@Override
	public String getPath() {
		throw new UnsupportedOperationException("Cannot retreive single index path from multiple loggers");
	}
	@Override
	public void save(String root, boolean index) {
		for(ResultLogger l : loggers){
			l.save(root, index);
		}
	}
	@Override
	public void setGlobalResult(String name, double value) {
		for(ResultLogger l : loggers){
			l.setGlobalResult(name, value);
		}
	}
	@Override
	public void setLocalResult(int index, String name, double value) {
		for(ResultLogger l : loggers){
			l.setLocalResult(index, name, value);
		}
	}
	@Override
	public ResultLogger spawnGroup(String name, int index) {
		JointResultLogger rtn = new JointResultLogger();
		rtn.loggers = new ResultLogger[this.loggers.length];
		for(int i=0; i<this.loggers.length; i++){
			rtn.loggers[i] = this.loggers[i].spawnGroup(name, index);
		}
		return rtn;
	}
	@Override
	public void suggestFlush() {
		for(ResultLogger l : loggers){
			l.suggestFlush();
		}
	}
}
