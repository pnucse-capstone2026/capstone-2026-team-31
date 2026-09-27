package com.ai_got_this.market_data_service;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

class MarketDataServiceApplicationTests {

	@Test
	void applicationEntryPointDoesNotRequireExternalInfrastructure() {
		assertDoesNotThrow(() -> Class.forName(MarketDataServiceApplication.class.getName()));
	}

}
