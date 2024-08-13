package com.jzo2o.api.customer;

import com.jzo2o.api.customer.dto.response.AddressBookResDTO;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;

public interface AddressBookApi {
    public AddressBookResDTO detail(@PathVariable("id") Long id);
    public List<AddressBookResDTO> getByUserIdAndCity(@RequestParam("userId") Long userId, @RequestParam("city") String city);
}
