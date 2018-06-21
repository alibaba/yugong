package com.taobao.yugong.common.model.position;

import com.taobao.yugong.common.model.ProgressStatus;

import java.util.ArrayList;
import java.util.List;

/**
 * 数据库增量或者全量的中间状态值
 *
 * @author agapple 2013-9-22 下午3:10:18
 */
public class IdPosition extends Position {

  private static final long serialVersionUID = -714239193409898959L;
  private Number id;
  private List<ProgressStatus> progressHistory = new ArrayList<ProgressStatus>();
  private ProgressStatus currentProgress = ProgressStatus.UNKNOW;
  private String updateTimeString;

  public IdPosition() {
  }

  public String getUpdateTimeString() {
    return updateTimeString;
  }

  public void setUpdateTimeString(String updateTimeString) {
    this.updateTimeString = updateTimeString;
  }

  public Number getId() {
    return id;
  }

  public void setId(Number id) {
    this.id = id;
  }

  public List<ProgressStatus> getProgressHistory() {
    return progressHistory;
  }

  public void setProgressHistory(List<ProgressStatus> progressHistory) {
    this.progressHistory = progressHistory;
  }

  public ProgressStatus getCurrentProgress() {
    return currentProgress;
  }

  public void setCurrentProgress(ProgressStatus currentProgress) {
    if (this.currentProgress != currentProgress && this.currentProgress != ProgressStatus.UNKNOW) {
      this.progressHistory.add(this.currentProgress); // 记录一下历史progress
    }

    this.currentProgress = currentProgress;
  }

  public boolean isInHistory(ProgressStatus progress) {
    return this.progressHistory.contains(progress);
  }

  public IdPosition clone() {
    IdPosition position = new IdPosition();
    position.setId(id);
    position.setCurrentProgress(currentProgress);
    position.setProgressHistory(progressHistory);
    position.setUpdateTimeString(updateTimeString);
    return position;
  }

  @Override
  public int hashCode() {
    final int prime = 31;
    int result = 1;
    result = prime * result + ((currentProgress == null) ? 0 : currentProgress.hashCode());
    result = prime * result + ((id == null) ? 0 : id.hashCode());
    result = prime * result + ((progressHistory == null) ? 0 : progressHistory.hashCode());
    return result;
  }

  @Override
  public boolean equals(Object obj) {
    if (this == obj) return true;
    if (obj == null) return false;
    if (getClass() != obj.getClass()) return false;
    IdPosition other = (IdPosition) obj;
    if (currentProgress != other.currentProgress) return false;
    if (id == null) {
      if (other.id != null) return false;
    } else if (!id.equals(other.id)) return false;
    if (progressHistory == null) {
      if (other.progressHistory != null) return false;
    } else if (!progressHistory.equals(other.progressHistory)) return false;
    if (updateTimeString == null) {
      if (other.updateTimeString != null) return false;
    } else if (!updateTimeString.equals(other.updateTimeString)) return false;
    return true;
  }

}
