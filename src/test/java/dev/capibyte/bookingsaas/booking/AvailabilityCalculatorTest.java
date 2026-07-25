package dev.capibyte.bookingsaas.booking;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalTime;
import java.util.List;
import org.junit.jupiter.api.Test;

class AvailabilityCalculatorTest {

	private final AvailabilityCalculator calculator = new AvailabilityCalculator();

	@Test
	void slicesAnOpenWindowIntoFixedDurationSlots() {
		List<TimeSlot> window = List.of(new TimeSlot(LocalTime.of(9, 0), LocalTime.of(12, 0)));

		List<TimeSlot> slots = calculator.freeSlots(window, List.of(), 60);

		assertThat(slots).containsExactly(
				new TimeSlot(LocalTime.of(9, 0), LocalTime.of(10, 0)),
				new TimeSlot(LocalTime.of(10, 0), LocalTime.of(11, 0)),
				new TimeSlot(LocalTime.of(11, 0), LocalTime.of(12, 0)));
	}

	@Test
	void dropsATrailingPartialSlotThatDoesNotFitTheDuration() {
		List<TimeSlot> window = List.of(new TimeSlot(LocalTime.of(9, 0), LocalTime.of(10, 30)));

		List<TimeSlot> slots = calculator.freeSlots(window, List.of(), 60);

		assertThat(slots).containsExactly(new TimeSlot(LocalTime.of(9, 0), LocalTime.of(10, 0)));
	}

	@Test
	void splitsAroundABlockedRangeInTheMiddleOfTheWindow() {
		List<TimeSlot> window = List.of(new TimeSlot(LocalTime.of(9, 0), LocalTime.of(18, 0)));
		List<TimeSlot> blocked = List.of(new TimeSlot(LocalTime.of(13, 0), LocalTime.of(14, 0)));

		List<TimeSlot> slots = calculator.freeSlots(window, blocked, 60);

		assertThat(slots).doesNotContain(new TimeSlot(LocalTime.of(13, 0), LocalTime.of(14, 0)));
		assertThat(slots).contains(
				new TimeSlot(LocalTime.of(12, 0), LocalTime.of(13, 0)),
				new TimeSlot(LocalTime.of(14, 0), LocalTime.of(15, 0)));
		assertThat(slots).hasSize(8);
	}

	@Test
	void aBlockedRangeCoveringTheWholeWindowLeavesNoSlots() {
		List<TimeSlot> window = List.of(new TimeSlot(LocalTime.of(9, 0), LocalTime.of(18, 0)));
		List<TimeSlot> blocked = List.of(new TimeSlot(LocalTime.of(0, 0), LocalTime.of(23, 59)));

		List<TimeSlot> slots = calculator.freeSlots(window, blocked, 60);

		assertThat(slots).isEmpty();
	}

	@Test
	void aBlockedRangeThatDoesNotOverlapLeavesTheWindowUntouched() {
		List<TimeSlot> window = List.of(new TimeSlot(LocalTime.of(9, 0), LocalTime.of(12, 0)));
		List<TimeSlot> blocked = List.of(new TimeSlot(LocalTime.of(14, 0), LocalTime.of(15, 0)));

		List<TimeSlot> slots = calculator.freeSlots(window, blocked, 60);

		assertThat(slots).hasSize(3);
	}

	@Test
	void multipleBlockedRangesAreAllSubtracted() {
		List<TimeSlot> window = List.of(new TimeSlot(LocalTime.of(9, 0), LocalTime.of(13, 0)));
		List<TimeSlot> blocked = List.of(
				new TimeSlot(LocalTime.of(9, 0), LocalTime.of(10, 0)),
				new TimeSlot(LocalTime.of(11, 0), LocalTime.of(12, 0)));

		List<TimeSlot> slots = calculator.freeSlots(window, blocked, 60);

		assertThat(slots).containsExactly(
				new TimeSlot(LocalTime.of(10, 0), LocalTime.of(11, 0)),
				new TimeSlot(LocalTime.of(12, 0), LocalTime.of(13, 0)));
	}
}
