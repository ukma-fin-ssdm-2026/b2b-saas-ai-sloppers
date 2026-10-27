package ua.edu.ukma.community.organization;

import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import ua.edu.ukma.community.common.error.ApiException;
import ua.edu.ukma.community.storage.ImageService;

import java.util.UUID;


@Service
public class OrganizationLogoService {

    private final OrganizationService organizations;
    private final ImageService images;

    public OrganizationLogoService(OrganizationService organizations, ImageService images) {
        this.organizations = organizations;
        this.images = images;
    }

    public Organization upload(UUID organizationId, MultipartFile file) {
        Organization current = organizations.get(organizationId);
        String url = images.store(folder(organizationId), "logo", file);
        Organization updated;
        try {
            updated = organizations.changeLogo(organizationId, url);
        } catch (ApiException e) {
            // організацію видалили, поки вантажився файл: прибираємо щойно завантажене
            images.deleteQuietly(url);
            throw e;
        }
        images.deleteQuietly(current.logoUrl());
        return updated;
    }

    public void delete(UUID organizationId) {
        Organization current = organizations.get(organizationId);
        if (current.logoUrl() == null) {
            return;
        }
        organizations.changeLogo(organizationId, null);
        images.deleteQuietly(current.logoUrl());
    }

    static String folder(UUID organizationId) {
        return "organizations/" + organizationId;
    }
}
