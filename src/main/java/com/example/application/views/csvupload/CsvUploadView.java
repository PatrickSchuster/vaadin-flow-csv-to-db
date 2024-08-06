package com.example.application.views.csvupload;

import com.vaadin.flow.component.orderedlayout.FlexLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.orderedlayout.FlexLayout.FlexWrap;
import com.vaadin.flow.component.upload.Upload;
import com.vaadin.flow.component.upload.receivers.MemoryBuffer;
import com.example.application.model.Address;
import com.example.application.model.User;
import com.example.application.repository.UserRepository;
import com.opencsv.CSVParserBuilder;
import com.opencsv.CSVReaderBuilder;
import com.opencsv.exceptions.CsvException;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.combobox.ComboBox;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.html.H1;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.notification.NotificationVariant;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.router.RouteAlias;
import com.vaadin.flow.shared.util.SharedUtil;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@PageTitle("CSV Uploader")
@Route(value = "")
@RouteAlias(value = "")
public class CsvUploadView extends VerticalLayout {
    private final UserRepository userRepository;

    private Grid<String[]> grid = new Grid<>();
    private Map<String, Integer> headerToColumnIndexMap = new HashMap<>();
    
    private Map<String, ComboBox<String>> comboBoxes = Map.of(
        "first", new ComboBox<>("First"),
        "last", new ComboBox<>("Last"),
        "address", new ComboBox<>("Address"),
        "zip", new ComboBox<>("Zip"),
        "country", new ComboBox<>("Country")
    );

    private Button saveBtn = new Button("Save");

    private final String IGNORE = "Ignore";

    private List<String[]> csvDataEntries;

    public CsvUploadView(UserRepository userRepository) {
        this.userRepository = userRepository;
        var memoryBuffer = new MemoryBuffer();
        var upload = new Upload(memoryBuffer);
        upload.addSucceededListener(e -> {
            renderCsv(memoryBuffer.getInputStream());
            assembleAndAddComboBoxes();
            assembleAndAddSaveBtn();
        });
        add(
            new H1("Upload your CSV to be displayed below"),
            grid,
            upload
        );
    }

    private void assembleAndAddComboBoxes() {
        comboBoxes.values().forEach(comboBox -> {
            comboBox.addValueChangeListener(event -> {
                enableSaveBtnIfAllComboBoxesHaveSelectedValue();
            });
        });
        var comboBoxContainer = new FlexLayout(
            comboBoxes.get("first"),
            comboBoxes.get("last"),
            comboBoxes.get("address"),
            comboBoxes.get("zip"),
            comboBoxes.get("country")
        );
        comboBoxContainer.setWidthFull();
        comboBoxContainer.setJustifyContentMode(JustifyContentMode.BETWEEN);
        comboBoxContainer.setFlexWrap(FlexWrap.WRAP);
        add(
            new H1("Map the CSV columns to the DB columns"),
            comboBoxContainer
        );
    }

    private void enableSaveBtnIfAllComboBoxesHaveSelectedValue() {
        boolean allSelected = comboBoxes.values()
            .stream()
            .allMatch(comboBox -> comboBox.getValue() != null);
        saveBtn.setEnabled(allSelected);
    }

    private void assembleAndAddSaveBtn() {
        saveBtn.setEnabled(false);
        saveBtn.addClickListener(event -> onSaveBtnClick());
        add(saveBtn);
    }

    private void renderCsv(InputStream inputStream) {
        var csvFileReader = new InputStreamReader(inputStream, StandardCharsets.UTF_8);
        var parser = new CSVParserBuilder().withSeparator(';').build();
        var reader = new CSVReaderBuilder(csvFileReader).withCSVParser(parser).build();
        fillComboBoxes();
        try {
            csvDataEntries = reader.readAll();
            String[] headers = csvDataEntries.get(0);
            for (int i = 0; i < headers.length; i++) {
                final int columnIndex = i;
                String header = headers[i];
                headerToColumnIndexMap.put(header, columnIndex);
                String humanReadableHeader = SharedUtil.camelCaseToHumanFriendly(header);
                grid.addColumn(str -> str[columnIndex]).setHeader(humanReadableHeader);
            }
            grid.setItems(csvDataEntries.subList(1, csvDataEntries.size()));
        } catch (IOException | CsvException e) {
            grid.addColumn(nop -> "Unable to load CSV: " + e.getMessage()).setHeader("Failed to import CSV file");
        }
    }

    private void fillComboBoxes() {
        List<String> options = new ArrayList<String>();
        options.add("First Name");
        options.add("Last Name");
        options.add("Street");
        options.add("Post Code");
        options.add("Country");
        options.add(IGNORE);
        comboBoxes.values().forEach(comboBox -> {
            comboBox.setItems(options);
        });
    }

    private void onSaveBtnClick() {
        String firstNameMappedValue = comboBoxes.get("first").getValue();
        String lastNameMappedValue = comboBoxes.get("last").getValue();
        String addressMappedValue = comboBoxes.get("address").getValue();
        String zipMappedValue = comboBoxes.get("zip").getValue();
        String countryMappedValue = comboBoxes.get("country").getValue();

        try {
            for (String[] row : csvDataEntries.subList(1, csvDataEntries.size())) {
                Address address = new Address();
                address.setStreet(getColumnValue(row, addressMappedValue));
                address.setPostCode(getColumnValue(row, zipMappedValue));
                address.setCountry(getColumnValue(row, countryMappedValue));
    
                User user = new User();
                user.setFirstName(getColumnValue(row, firstNameMappedValue));
                user.setLastName(getColumnValue(row, lastNameMappedValue));
                user.setAddress(address);
                
                userRepository.save(user);
            }
            Notification notification = Notification.show("User saved");
            notification.addThemeVariants(NotificationVariant.LUMO_SUCCESS);
        } catch (Exception e) {
            Notification notification = Notification.show("Something went wrong");
            notification.addThemeVariants(NotificationVariant.LUMO_ERROR);
        }
    }

    private String getColumnValue(String[] row, String entityFieldName) {
        if (IGNORE.equals(entityFieldName)) {
            return null;
        }
        var csvHeaderName = getCsvHeaderName(entityFieldName);
        int index = headerToColumnIndexMap.getOrDefault(csvHeaderName, -1);
        return index != -1 ? row[index] : null;
    }

    private String getCsvHeaderName(String entityFieldName) {
        switch (entityFieldName) {
            case "First Name":
                return "first";
            case "Last Name":
                return "last";
            case "Street":
                return "address";
            case "Post Code":
                return "zip";
            case "Country":
                return "country";
            default:
                return "";
        }
    }
}
