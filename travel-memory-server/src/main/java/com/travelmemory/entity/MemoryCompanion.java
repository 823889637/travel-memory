package com.travelmemory.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;

@TableName("memory_companion")
public class MemoryCompanion {

    private Long memoryId;
    private Long companionId;
    private LocalDateTime createTime;

    public Long getMemoryId() { return memoryId; }
    public void setMemoryId(Long memoryId) { this.memoryId = memoryId; }
    public Long getCompanionId() { return companionId; }
    public void setCompanionId(Long companionId) { this.companionId = companionId; }
    public LocalDateTime getCreateTime() { return createTime; }
    public void setCreateTime(LocalDateTime createTime) { this.createTime = createTime; }
}
