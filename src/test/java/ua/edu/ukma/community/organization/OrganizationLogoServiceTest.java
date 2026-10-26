package ua.edu.ukma.community.organization;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import ua.edu.ukma.community.common.error.ApiException;
import ua.edu.ukma.community.common.error.ErrorCode;
import ua.edu.ukma.community.storage.ImageService;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static ua.edu.ukma.community.support.TestFixtures.FIXED_CLOCK;
import static ua.edu.ukma.community.support.TestFixtures.ORG_ID;

@ExtendWith(MockitoExtension.class)
@DisplayName("OrganizationLogoService: логотип організації")
class OrganizationLogoServiceTest {

    static final byte[] PNG = {(byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1A, '\n'};

    @Mock
    ImageService images;

    OrganizationService organizations;
    OrganizationLogoService logos;
    MockMultipartFile file = new MockMultipartFile("file", "logo.png", "image/png", PNG);

    @BeforeEach
    void setUp() {
        InMemoryOrganizationRepository repository = new InMemoryOrganizationRepository(FIXED_CLOCK);
        List<Object> published = new ArrayList<>();
        organizations = new OrganizationService(repository, published::add, FIXED_CLOCK);
        logos = new OrganizationLogoService(organizations, images);
    }

    @Test
    @DisplayName("завантаження зберігає URL у папці організації")
    void uploadSetsLogoUrl() {
        when(images.store(eq("organizations/" + ORG_ID), eq("logo"), any())).thenReturn("https://cdn/new.png");

        Organization updated = logos.upload(ORG_ID, file);

        assertThat(updated.logoUrl()).isEqualTo("https://cdn/new.png");
        assertThat(organizations.get(ORG_ID).logoUrl()).isEqualTo("https://cdn/new.png");
        // інші поля не зачеплено
        assertThat(updated.name()).isEqualTo("Дебатний клуб НаУКМА");
    }

    @Test
    @DisplayName("заміна логотипу видаляє попередній файл")
    void replaceDeletesOldFile() {
        when(images.store(anyString(), anyString(), any())).thenReturn("https://cdn/old.png", "https://cdn/new.png");
        logos.upload(ORG_ID, file);

        logos.upload(ORG_ID, file);

        verify(images).deleteQuietly("https://cdn/old.png");
        assertThat(organizations.get(ORG_ID).logoUrl()).isEqualTo("https://cdn/new.png");
    }

    @Test
    @DisplayName("видалення прибирає URL і файл; без логотипу — нічого не робить")
    void deleteClearsLogo() {
        when(images.store(anyString(), anyString(), any())).thenReturn("https://cdn/old.png");
        logos.upload(ORG_ID, file);

        logos.delete(ORG_ID);
        logos.delete(ORG_ID);

        assertThat(organizations.get(ORG_ID).logoUrl()).isNull();
        verify(images).deleteQuietly("https://cdn/old.png");
    }

    @Test
    @DisplayName("неіснуюча організація → 404 ще до завантаження файлу")
    void unknownOrganization() {
        assertThatThrownBy(() -> logos.upload(java.util.UUID.randomUUID(), file))
                .extracting(e -> ((ApiException) e).code()).isEqualTo(ErrorCode.ORGANIZATION_NOT_FOUND);
        verify(images, never()).store(anyString(), anyString(), any());
    }
}
