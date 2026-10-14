package com.careerconnect.common.pagination;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PageRequestDtoTest {

    @Test
    @DisplayName("Default page request should have page=0, size=20, offset=0")
    void shouldInitializeWithDefaults() {
        PageRequestDto dto = new PageRequestDto();

        assertThat(dto.getPage()).isEqualTo(0);
        assertThat(dto.getSize()).isEqualTo(20);
        assertThat(dto.getOffset()).isEqualTo(0);
    }

    @Test
    @DisplayName("Offset calculation should equal page * size")
    void shouldCalculateOffsetCorrectly() {
        PageRequestDto dto = new PageRequestDto(3, 25);

        assertThat(dto.getPage()).isEqualTo(3);
        assertThat(dto.getSize()).isEqualTo(25);
        assertThat(dto.getOffset()).isEqualTo(75);
    }

    @Test
    @DisplayName("Size exceeding maximum of 100 should be capped at 100")
    void shouldCapSizeAtMax() {
        PageRequestDto dto = new PageRequestDto(0, 500);

        assertThat(dto.getSize()).isEqualTo(100);
    }
}
