package br.com.fiap.t2tdsps.projectmgt.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

@Data	
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Milestone {

    private Long id;
    private Long projectId;
    private String name;
    private LocalDate dueDate;
    private MilestoneStatus status;
}