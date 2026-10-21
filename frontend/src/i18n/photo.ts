import { useIntl } from 'umi';

type IntlShape = ReturnType<typeof useIntl>;

const tagKey = (value: string) => {
  const normalized = value.trim().toLowerCase().replace(/[\s_-]+/g, '');
  const aliases: Record<string, string> = {
    person: 'people', portrait: 'people', temple: 'temple', shrine: 'shrine',
    golden: 'gold', grey: 'gray', softlight: 'softLight', softlighting: 'softLight',
    diffusedlight: 'diffused', diffuselight: 'diffused', naturallight: 'naturalLight',
    naturallighting: 'naturalLight', daylight: 'naturalLight', lowlight: 'lowLight',
    lowlighting: 'lowLight', backlight: 'backlit', foggy: 'foggy',
    stonesteps: 'stoneSteps', stonepath: 'stonePath', snowfall: 'snowfall', snow: 'snow',
    overcast: 'overcast', cloudysky: 'overcast', traditionalarchitecture: 'traditionalArchitecture',
    traditionalbuilding: 'traditionalArchitecture', entrance: 'entrance', doorway: 'entrance',
    redlanterns: 'redLanterns', redlantern: 'redLanterns', courtyard: 'courtyard',
    winter: 'winter', autumn: 'autumn', spring: 'spring', summer: 'summer',
    walking: 'walking', symmetry: 'symmetry', symmetrical: 'symmetry', naturalframing: 'naturalFraming',
    layered: 'layered', depth: 'depth', texture: 'texture', historic: 'historic', cultural: 'cultural',
  };
  return aliases[normalized] ?? normalized;
};

export const localizedPhotoTagName = (value: string, intl: IntlShape) => {
  const id = `tag.${tagKey(value)}`;
  const known = (intl.messages as Record<string, string | undefined>)?.[id];
  if (known) return intl.formatMessage({ id });
  return value;
};

/** Keeps the original AI text untouched and avoids exposing stored Chinese prose in Japanese mode. */
export const localizedPhotoDescription = (photo: API.Photo, intl: IntlShape) => {
  if (!intl.locale.toLowerCase().startsWith('ja')) return photo.aiDescription || undefined;
  if (photo.localizedAiDescription) return photo.localizedAiDescription;
  return intl.formatMessage({ id: 'photo.ai.descriptionUnavailable' });
};
