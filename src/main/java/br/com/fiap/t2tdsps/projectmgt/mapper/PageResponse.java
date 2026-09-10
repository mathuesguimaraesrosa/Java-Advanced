package br.com.fiap.t2tdsps.projectmgt.mapper;

import java.util.List;

import org.springframework.data.domain.Page;

public record PageResponse<T>(List<T> content, int page, int size, Long totalElements, int totalPage, boolean last) {
	public static <T> PageResponse<T> from(Page<T> page){
		return new PageResponse<T>(page.getContent(), page.getNumber(),page.getSize() , page.getTotalElements(), page.getTotalPages(), page.isLast());
	}
}