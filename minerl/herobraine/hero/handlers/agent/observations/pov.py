# Copyright (c) 2020 All Rights Reserved
# Author: William H. Guss, Brandon Houghton


from minerl.herobraine.hero.handlers.translation import KeymapTranslationHandler
from minerl.herobraine.hero import spaces
from typing import Tuple
import numpy as np


class POVObservation(KeymapTranslationHandler):
    """
    Handles POV observations.
    """

    def to_string(self):
        return 'pov'

    def __repr__(self):
        result = f'POVObservation(video_resolution={self.video_resolution}'
        if self.include_depth:
            result += ', include_depth=True'
        result += ')'
        result = f'{result}:{self.to_string()}'
        return result

    def xml_template(self) -> str:
        return str("""
            <VideoProducer 
                want_depth="{{ include_depth | string | lower }}">
                <Width>{{ video_width }} </Width>
                <Height>{{ video_height }}</Height>
            </VideoProducer>""")

    def __init__(self, video_resolution: Tuple[int, int], include_depth: bool = False):
        self.include_depth = include_depth
        self.video_resolution = video_resolution
        self.video_depth = 3
        space = None
        if include_depth:
            space = spaces.Tuple((spaces.Box(0, 255, list(video_resolution)[::-1] + [3], dtype=np.uint8),
                                  spaces.Box(0., 1., list(video_resolution)[::-1] + [1], dtype=np.float32)))
        else:
            space = spaces.Box(0, 255, list(video_resolution)[::-1] + [3], dtype=np.uint8)

        # TODO (R): FIGURE THIS THE FUCK OUT & Document it.
        self.video_height = video_resolution[1]
        self.video_width = video_resolution[0]

        super().__init__(
            hero_keys=["pov"],
            univ_keys=["pov"], space=space)

    def from_hero(self, obs):
        byte_array = super().from_hero(obs)
        n_pixels = self.video_height * self.video_width
        rgb = np.frombuffer(byte_array, dtype=np.uint8, count=n_pixels * 3, offset=0)
        if self.include_depth:
            depth = np.frombuffer(byte_array, dtype=np.float32, count=n_pixels, offset=n_pixels * 3)

        if rgb is None or len(rgb) == 0:
            rgb = np.zeros((self.video_height, self.video_width, self.video_depth), dtype=np.uint8)
            if self.include_depth:
                depth = np.zeros((self.video_height, self.video_width, 1), dtype=np.float32)
        else:
            rgb = rgb.reshape((self.video_height, self.video_width, self.video_depth))[::-1, :, :]
            if self.include_depth:
                depth = depth.reshape((self.video_height, self.video_width, 1))[::-1, :, :]

        if self.include_depth:
            modelview_matrix = np.frombuffer(byte_array, dtype=np.float32, count=16, offset=n_pixels * 7)
            projection_matrix = np.frombuffer(byte_array, dtype=np.float32, count=16, offset=n_pixels * 7 + 16)
            import ipdb; ipdb.set_trace()

        return (rgb, depth) if self.include_depth else rgb

    def __or__(self, other):
        """
        Combines two POV observations into one. If all of the properties match return self
        otherwise raise an exception.
        """
        if isinstance(other, POVObservation) and self.include_depth == other.include_depth and \
                self.video_resolution == other.video_resolution:
            return POVObservation(self.video_resolution, include_depth=self.include_depth)
        else:
            raise ValueError("Incompatible observables!")
