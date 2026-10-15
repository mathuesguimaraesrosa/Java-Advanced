package br.com.fiap.t2tdsps.projectmgt.mapper;

import br.com.fiap.t2tdsps.projectmgt.dto.TagResponse;
import br.com.fiap.t2tdsps.projectmgt.model.Tag;

public final class TagMapper {

    private TagMapper() {
    }

    public static TagResponse toResponse(Tag tag) {
        return new TagResponse(tag.getId(), tag.getName());
    }
}
