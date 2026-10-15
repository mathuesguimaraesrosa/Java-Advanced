package br.com.fiap.t2tdsps.projectmgt.dto;

import java.util.List;

public record TaskResponse(Long id, Long milestoneId, String title, Boolean done, Long assigneeId,
                           List<TagResponse> tags) {
}
